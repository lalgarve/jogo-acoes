"""HTTP orchestrations the generator needs -- login, competition creation, and the three ways a
player ends up `IN_COMPETITION` (public entry, private invite, an already-registered player
confirming). Each one talks to the real API via the generated client (`docs/openapi.yaml`) plus
`common.blackbox_fixtures` for reading e-mails -- the same mechanics already exercised by
`features/steps/public_competition_entry_steps.py`, just reusable outside a Gherkin scenario.
"""

import datetime

from common.blackbox_fixtures import last_email
from jogo_acoes_client import Client
from jogo_acoes_client.api.competitions import create_competition, decide_invite_email_timing
from jogo_acoes_client.api.entry_requests import request_or_confirm_entry
from jogo_acoes_client.api.login import complete_registration, consume_login_link, request_login_link
from jogo_acoes_client.api.players import invite_players
from jogo_acoes_client.models import Competition

from . import factories


def _token_from_link(link: str) -> str:
    # last_email's link is a path like "/login-links/<token>" or
    # "/login-links/<token>/registration" -- both share the same token as the second segment.
    return link.strip("/").split("/")[1]


def new_client(base_url: str) -> Client:
    return Client(base_url=base_url)


def admin_login(base_url: str, admin_email: str) -> Client:
    client = new_client(base_url)
    response = request_login_link.sync_detailed(client=client, body=factories.login_link_request(admin_email))
    if response.status_code != 202:
        raise RuntimeError(f"admin login request failed: {response.status_code} {response.content!r}")

    link = last_email(base_url, admin_email).link
    login_result = consume_login_link.sync(client=client, token=_token_from_link(link))
    if login_result is None:
        raise RuntimeError("admin login link should already be a registered account")
    return client


def create_public_competition(
    admin_client: Client,
    name: str,
    start_date: datetime.date,
    *,
    duration_days: int = 30,
    buy_fee: float = 0.5,
    sell_fee: float = 0.5,
    recurring: bool = False,
) -> Competition:
    competition = create_competition.sync(
        client=admin_client,
        body=factories.public_competition_request(
            name, start_date, duration_days=duration_days, buy_fee=buy_fee, sell_fee=sell_fee, recurring=recurring
        ),
    )
    if competition is None or isinstance(competition, dict):
        raise RuntimeError(f"failed to create public competition {name!r}")
    return competition


def create_private_competition(
    admin_client: Client,
    name: str,
    start_date: datetime.date,
    invited_emails: list[str],
    timing: str,
    *,
    duration_days: int = 30,
    buy_fee: float = 0.5,
    sell_fee: float = 0.5,
) -> Competition:
    competition = create_competition.sync(
        client=admin_client,
        body=factories.private_competition_request(
            name, start_date, invited_emails, duration_days=duration_days, buy_fee=buy_fee, sell_fee=sell_fee
        ),
    )
    if competition is None or isinstance(competition, dict):
        raise RuntimeError(f"failed to create private competition {name!r}")

    decide_response = decide_invite_email_timing.sync_detailed(
        competition.id, client=admin_client, body=factories.invite_timing_request(timing)
    )
    if decide_response.status_code != 204:
        raise RuntimeError(f"failed to decide invite timing for {name!r}: {decide_response.status_code}")
    return competition


def _consume_and_register(base_url: str, link: str, name: str) -> Client:
    client = new_client(base_url)
    token = _token_from_link(link)

    still_needs_registration = consume_login_link.sync_detailed(token=token, client=client)
    if still_needs_registration.status_code != 202:
        raise RuntimeError(
            f"expected a brand new player at {link!r}, got {still_needs_registration.status_code}"
        )

    registration = complete_registration.sync_detailed(
        token=token, client=client, body=factories.registration_request(name)
    )
    if registration.status_code != 200:
        raise RuntimeError(f"registration failed for {link!r}: {registration.status_code} {registration.content!r}")
    return client


def public_entry_new_player(base_url: str, competition_id: int, email: str, name: str) -> Client:
    """A brand new player enters a PUBLIC competition: request entry, read the link, register."""
    client = new_client(base_url)
    response = request_or_confirm_entry.sync_detailed(
        competition_id, client=client, body=factories.entry_request(email=email, captcha_token="")
    )
    if response.status_code != 202:
        raise RuntimeError(f"public entry request failed for {email}: {response.status_code} {response.content!r}")

    link = last_email(base_url, email).link
    return _consume_and_register(base_url, link, name)


def complete_invited_registration(base_url: str, email: str, name: str) -> Client:
    """A newly-invited (private competition) player follows their invite link and registers --
    same completion mechanics as a public entrant, but there's no entry-request first: the
    invite already created their participation.
    """
    link = last_email(base_url, email).link
    return _consume_and_register(base_url, link, name)


def login_existing_player(base_url: str, email: str) -> Client:
    """An already-registered player logs in via a fresh magic link."""
    client = new_client(base_url)
    response = request_login_link.sync_detailed(client=client, body=factories.login_link_request(email))
    if response.status_code != 202:
        raise RuntimeError(f"login request failed for {email}: {response.status_code} {response.content!r}")

    link = last_email(base_url, email).link
    login_result = consume_login_link.sync(client=client, token=_token_from_link(link))
    if login_result is None:
        raise RuntimeError(f"expected {email} to already be a registered player")
    return client


def confirm_entry(client: Client, competition_id: int) -> None:
    """An already-logged-in player confirms entry into a competition they were invited to (or a
    public one) -- no request body, per `request_or_confirm_entry`'s auth-state behavior.
    """
    response = request_or_confirm_entry.sync_detailed(competition_id, client=client)
    if response.status_code != 200:
        raise RuntimeError(f"entry confirmation failed: {response.status_code} {response.content!r}")


def invite_existing_player(admin_client: Client, competition_id: int, email: str) -> None:
    """Invites an already-registered player into an existing (already OPEN) private
    competition -- the mechanism behind C8's players and M4's pending invite.
    """
    response = invite_players.sync_detailed(
        competition_id, client=admin_client, body=factories.invite_players_request([email])
    )
    if response.status_code != 202:
        raise RuntimeError(f"invite failed for {email}: {response.status_code} {response.content!r}")
