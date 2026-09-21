"""Object Mother-style factories for the API request bodies the generator sends -- same pattern
as `CompetitionMother`/`UserMother` (`memory/constitution.md`): every function returns a
valid-by-default payload, and keyword arguments override one field at a time.

`start_date` has no default on purpose: it must be computed from `--clock-offset-days` (see
`seed/__main__.py`), never from the script's own `datetime.date.today()` -- the script runs on
the host, with the real clock, while the app it talks to has its clock shifted into the past
(see specs/05-018-gerador-dados-teste/plan.md). A default here would silently use the wrong
"today" and break every duration/end-day calculation the catalog relies on.
"""

import datetime

from jogo_acoes_client.models import (
    CompetitionCreateRequest,
    CompetitionType,
    CompleteRegistrationBody,
    DecideInviteEmailTimingBody,
    DecideInviteEmailTimingBodyTiming,
    EntryRequest,
    InvitePlayersBody,
    RequestLoginLinkBody,
)
from jogo_acoes_client.types import UNSET, Unset


def public_competition_request(
    name: str,
    start_date: datetime.date,
    *,
    duration_days: int = 30,
    buy_fee: float = 0.5,
    sell_fee: float = 0.5,
    recurring: bool = False,
) -> CompetitionCreateRequest:
    return CompetitionCreateRequest(
        name=name,
        type_=CompetitionType.PUBLIC,
        start_date=start_date,
        duration_days=duration_days,
        buy_fee=buy_fee,
        sell_fee=sell_fee,
        recurring=recurring,
    )


def private_competition_request(
    name: str,
    start_date: datetime.date,
    emails: list[str],
    *,
    duration_days: int = 30,
    buy_fee: float = 0.5,
    sell_fee: float = 0.5,
) -> CompetitionCreateRequest:
    return CompetitionCreateRequest(
        name=name,
        type_=CompetitionType.PRIVATE,
        start_date=start_date,
        duration_days=duration_days,
        buy_fee=buy_fee,
        sell_fee=sell_fee,
        emails=emails,
    )


def invite_timing_request(timing: str = "now") -> DecideInviteEmailTimingBody:
    return DecideInviteEmailTimingBody(timing=DecideInviteEmailTimingBodyTiming(timing))


def entry_request(email: str | Unset = UNSET, captcha_token: str = "") -> EntryRequest:
    """A brand new/logged-out player's entry request needs `email`; an already-logged-in
    player confirming entry sends no body at all (see `request_or_confirm_entry`) -- callers
    that need that case skip this factory entirely.
    """
    return EntryRequest(email=email, captcha_token=captcha_token)


def login_link_request(email: str) -> RequestLoginLinkBody:
    return RequestLoginLinkBody(email=email)


def registration_request(name: str) -> CompleteRegistrationBody:
    return CompleteRegistrationBody(name=name)


def invite_players_request(emails: list[str]) -> InvitePlayersBody:
    return InvitePlayersBody(emails=emails)
