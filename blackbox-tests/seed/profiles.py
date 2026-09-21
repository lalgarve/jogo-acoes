"""Catalog of competitions and players the generator creates, spec 05-018.

See specs/05-018-gerador-dados-teste/plan.md for the full table and the reasoning behind each
row -- this module only turns that table into data. Every competition runs against the same
single `--clock-offset-days` (no per-row clock): `startDate` is always "the day after the
shifted clock", and each row's own `duration_days` decides whether it ends up "terminada por
data" (short) or "em andamento" (long) relative to that one offset.
"""

from dataclasses import dataclass


@dataclass(frozen=True)
class CompetitionSpec:
    code: str
    name_suffix: str
    type: str  # "PUBLIC" | "PRIVATE"
    duration_days: int
    recurring: bool = False
    buy_fee: float = 0.5
    sell_fee: float = 0.5
    invite_timing: str | None = None  # "now" | "later" -- PRIVATE only
    invited_count: int = 0  # PRIVATE only: e-mails invited at creation time
    invited_are_preexisting_players: bool = False  # C8: reuses C2's already-registered players
    confirmed_count: int = 0  # of the invited/entering players, how many complete the flow
    public_entrants: int = 0  # PUBLIC only: new players entering via public entry


CATALOG: dict[str, CompetitionSpec] = {
    "C1": CompetitionSpec("C1", "Copa publica", "PUBLIC", duration_days=30),
    "C2": CompetitionSpec(
        "C2", "Copa publica com jogadores", "PUBLIC", duration_days=30, public_entrants=5, confirmed_count=5
    ),
    "C3": CompetitionSpec(
        "C3",
        "Copa publica recorrente",
        "PUBLIC",
        duration_days=30,
        recurring=True,
        public_entrants=2,
        confirmed_count=2,
    ),
    "C4": CompetitionSpec(
        "C4", "Copa privada aguardando convites", "PRIVATE", duration_days=30, invite_timing="later", invited_count=3
    ),
    "C5": CompetitionSpec(
        "C5", "Copa privada com convites enviados", "PRIVATE", duration_days=30, invite_timing="now", invited_count=3
    ),
    "C6": CompetitionSpec(
        "C6",
        "Copa privada parcialmente confirmada",
        "PRIVATE",
        duration_days=30,
        invite_timing="now",
        invited_count=3,
        confirmed_count=2,
    ),
    "C7": CompetitionSpec("C7", "Copa publica de taxa zero e duracao curta", "PUBLIC", duration_days=1, buy_fee=0, sell_fee=0),
    "C8": CompetitionSpec(
        "C8",
        "Copa privada com jogadores ja registrados",
        "PRIVATE",
        duration_days=30,
        invite_timing="now",
        invited_count=2,
        invited_are_preexisting_players=True,
    ),
    "C9": CompetitionSpec(
        "C9", "Copa publica em andamento", "PUBLIC", duration_days=60, public_entrants=3, confirmed_count=3
    ),
    "C10": CompetitionSpec(
        "C10", "Copa publica terminada por data", "PUBLIC", duration_days=10, public_entrants=3, confirmed_count=3
    ),
    "C11": CompetitionSpec(
        "C11",
        "Copa privada em andamento",
        "PRIVATE",
        duration_days=60,
        invite_timing="now",
        invited_count=3,
        confirmed_count=2,
    ),
}

PROFILES: dict[str, list[str]] = {
    "minimal": ["C1", "C2", "C4"],
    "standard": list(CATALOG.keys()),
    "volume": [*CATALOG.keys(), "V1"],
}

VOLUME_COMPETITION_CODE = "V1"
VOLUME_PLAYER_COUNT = 200

# Each Mn joins the first n competitions of this list, in order; M4's 4th "competition" isn't
# here -- it's the C5 invite it deliberately never confirms (see __main__.seed_multi_competition_players).
MULTI_COMPETITION_JOIN_ORDER = ["C9", "C10", "C11"]
MULTI_COMPETITION_PENDING_INVITE = "C5"
