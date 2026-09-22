"""CLI entry point for the blackbox data generator, spec 05-018.

Usage:
    python -m seed --profile standard --clock-offset-days -15 [--seed 42] [--dry-run]

Assumes the blackbox environment (spec 05-014) is already running with its clock shifted by
`--clock-offset-days` (see `scripts/blackbox-clock-offset.sh`) -- this script only talks HTTP to
it, it never starts/stops containers (see spec.md, "Fora de escopo"). Runs once, against a
freshly-booted environment: it doesn't check whether the catalog already exists before creating
it (see spec.md, "Requisitos funcionais").
"""

import argparse
import datetime
import os
import random
import sys
from collections.abc import Sequence

from common.blackbox_fixtures import ADMIN_EMAIL, last_email
from jogo_acoes_client import Client
from jogo_acoes_client.models import Competition, CompetitionType

from . import flows
from .profiles import (
    CATALOG,
    MULTI_COMPETITION_JOIN_ORDER,
    MULTI_COMPETITION_PENDING_INVITE,
    PROFILES,
    VOLUME_COMPETITION_CODE,
    VOLUME_PLAYER_COUNT,
    CompetitionSpec,
)


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--profile", choices=sorted(PROFILES), default="standard")
    parser.add_argument(
        "--clock-offset-days",
        type=int,
        required=True,
        dest="clock_offset_days",
        help="Days in the past the app's clock is shifted by (must be negative)",
    )
    parser.add_argument("--seed", type=int, default=0, help="Only affects the 'volume' profile's player names")
    parser.add_argument("--base-url", default=os.environ.get("API_BASE_URL", "http://localhost:8080/api"))
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args(argv)

    if args.clock_offset_days >= 0:
        parser.error("--clock-offset-days must be negative -- this generator only backfills the past")

    return args


def player_email(competition_code: str, index: int) -> str:
    return f"success+seed-{competition_code.lower()}-jogador-{index}@simulator.amazonses.com"


def player_name(competition_code: str, index: int) -> str:
    return f"Seed {competition_code} Jogador {index}"


def verify_clock(base_url: str, offset_days: int, checked_email: str) -> None:
    expected_date = datetime.date.today() + datetime.timedelta(days=offset_days)
    sent = last_email(base_url, checked_email)
    if sent.sent_at.date() != expected_date:
        raise RuntimeError(
            f"app clock mismatch: expected the app's 'today' to be {expected_date} "
            f"(offset {offset_days} from the real today), but its last e-mail was sent at "
            f"{sent.sent_at} -- is the app really running with that clock offset? "
            f"(see scripts/blackbox-clock-offset.sh)"
        )


def private_invite_emails(code: str, spec: CompetitionSpec) -> list[str]:
    if spec.invited_are_preexisting_players:
        # C8: reuses C2's already-registered players instead of fresh addresses -- C2 must run
        # before C8 in the catalog (dict/profile order), which it does.
        return [player_email("C2", i) for i in range(1, spec.invited_count + 1)]
    return [player_email(code, i) for i in range(1, spec.invited_count + 1)]


def create_catalog_competition(
    admin_client: Client, code: str, spec: CompetitionSpec, start_date: datetime.date
) -> Competition:
    name = f"[seed] {code} {spec.name_suffix}"
    if spec.type == "PUBLIC":
        return flows.create_public_competition(
            admin_client,
            name,
            start_date,
            duration_days=spec.duration_days,
            buy_fee=spec.buy_fee,
            sell_fee=spec.sell_fee,
            recurring=spec.recurring,
        )
    return flows.create_private_competition(
        admin_client,
        name,
        start_date,
        private_invite_emails(code, spec),
        spec.invite_timing or "now",
        duration_days=spec.duration_days,
        buy_fee=spec.buy_fee,
        sell_fee=spec.sell_fee,
    )


def seed_catalog_players(
    base_url: str,
    code: str,
    spec: CompetitionSpec,
    competition: Competition,
    clock_offset_days: int,
    clock_checked: bool,
) -> tuple[int, bool]:
    """Creates the players a catalog row needs, and (once) verifies the app's clock against
    `--clock-offset-days` right after the first e-mail this run sends. Returns the number of
    players created and whether the clock was checked (so the caller only does it once).
    """
    can_seed_players = spec.type == "PUBLIC" or (spec.invite_timing == "now" and not spec.invited_are_preexisting_players)
    if not can_seed_players:
        return 0, clock_checked

    for i in range(1, spec.confirmed_count + 1):
        email = player_email(code, i)
        name = player_name(code, i)
        if spec.type == "PUBLIC":
            flows.public_entry_new_player(base_url, competition.id, email, name)
        else:
            flows.complete_invited_registration(base_url, email, name)

        if not clock_checked:
            verify_clock(base_url, clock_offset_days, email)
            clock_checked = True
    return spec.confirmed_count, clock_checked


def seed_multi_competition_players(base_url: str, admin_client: Client, competitions: dict[str, Competition]) -> int:
    created = 0
    for depth, code in enumerate(("M1", "M2", "M3", "M4"), start=1):
        email = f"success+seed-multi-{code.lower()}@simulator.amazonses.com"
        name = f"Seed Multi {code}"
        joins = MULTI_COMPETITION_JOIN_ORDER[:depth]

        flows.public_entry_new_player(base_url, competitions[joins[0]].id, email, name)
        created += 1

        for extra_code in joins[1:]:
            competition = competitions[extra_code]
            if competition.type_ == CompetitionType.PRIVATE:
                flows.invite_existing_player(admin_client, competition.id, email)
            client = flows.login_existing_player(base_url, email)
            flows.confirm_entry(client, competition.id)

        if code == "M4":
            # Invited, deliberately never confirms -- the one pendingConfirmation entry.
            flows.invite_existing_player(admin_client, competitions[MULTI_COMPETITION_PENDING_INVITE].id, email)
    return created


def seed_volume_competition(base_url: str, admin_client: Client, start_date: datetime.date, seed: int) -> int:
    competition = flows.create_public_competition(
        admin_client, f"[seed] {VOLUME_COMPETITION_CODE} Copa de volume", start_date, duration_days=30
    )
    rng = random.Random(seed)
    for i in range(1, VOLUME_PLAYER_COUNT + 1):
        email = f"success+seed-{VOLUME_COMPETITION_CODE.lower()}-jogador-{i}@simulator.amazonses.com"
        name = f"Seed Volume Jogador {rng.randint(1, 10_000_000)}"
        flows.public_entry_new_player(base_url, competition.id, email, name)
    return VOLUME_PLAYER_COUNT


def run(args: argparse.Namespace) -> dict[str, int]:
    start_date = datetime.date.today() + datetime.timedelta(days=args.clock_offset_days + 1)
    codes = [code for code in PROFILES[args.profile] if code != VOLUME_COMPETITION_CODE]
    counts = {"competitions": 0, "players": 0}

    if args.dry_run:
        print(
            f"[dry-run] profile={args.profile} clock_offset_days={args.clock_offset_days} "
            f"start_date={start_date} competitions={codes}"
        )
        return counts

    try:
        admin_client = flows.admin_login(args.base_url, ADMIN_EMAIL)
    except RuntimeError as exc:
        sys.exit(f"Login do administrador falhou: {exc}")

    try:
        competitions: dict[str, Competition] = {}
        clock_checked = False

        for code in codes:
            spec = CATALOG[code]
            competitions[code] = create_catalog_competition(admin_client, code, spec, start_date)
            counts["competitions"] += 1

            players_created, clock_checked = seed_catalog_players(
                args.base_url, code, spec, competitions[code], args.clock_offset_days, clock_checked
            )
            counts["players"] += players_created

        has_full_catalog = all(code in competitions for code in (*MULTI_COMPETITION_JOIN_ORDER, MULTI_COMPETITION_PENDING_INVITE))
        if has_full_catalog:
            counts["players"] += seed_multi_competition_players(args.base_url, admin_client, competitions)

        if args.profile == "volume":
            counts["players"] += seed_volume_competition(args.base_url, admin_client, start_date, args.seed)
            counts["competitions"] += 1
    except RuntimeError as exc:
        sys.exit(f"Falha ao gerar dados de teste: {exc}")

    print(f"Criado: {counts['competitions']} competicoes, {counts['players']} jogadores")
    return counts


def main(argv: Sequence[str] | None = None) -> None:
    run(parse_args(argv))


if __name__ == "__main__":
    main()
