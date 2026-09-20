import datetime
import uuid

from behave import given, when, then

from environment import new_client
from mailbox import last_email_link

from jogo_acoes_client.api.competitions import create_competition
from jogo_acoes_client.api.entry_requests import request_or_confirm_entry
from jogo_acoes_client.api.login import complete_registration, consume_login_link, request_login_link
from jogo_acoes_client.api.my_competitions import get_competition_detail
from jogo_acoes_client.models import (
    AccessLevel,
    CompetitionCreateRequest,
    CompetitionType,
    CompleteRegistrationBody,
    EntryRequest,
    RequestLoginLinkBody,
)

ADMIN_EMAIL = "success+admin@simulator.amazonses.com"


def _token_from_link(link: str) -> str:
    # last_email_link returns a path like "/login-links/<token>" or
    # "/login-links/<token>/registration" -- both share the same token as the second segment.
    return link.strip("/").split("/")[1]


@given('the administrator is logged in via the magic link sent to "{email}"')
def step_admin_logs_in(context, email):
    assert email == ADMIN_EMAIL, "the blackbox environment only seeds one administrator"
    context.admin_client = new_client(context)

    response = request_login_link.sync_detailed(
        client=context.admin_client, body=RequestLoginLinkBody(email=email)
    )
    assert response.status_code == 202, response.content

    link = last_email_link(context.api_base_url, email)
    login_result = consume_login_link.sync(client=context.admin_client, token=_token_from_link(link))
    assert login_result is not None, "admin login link should already be registered"


@given("the administrator creates a public competition")
def step_admin_creates_competition(context):
    competition = create_competition.sync(
        client=context.admin_client,
        body=CompetitionCreateRequest(
            name=f"Blackbox python cup {uuid.uuid4()}",
            type_=CompetitionType.PUBLIC,
            start_date=datetime.date.today() + datetime.timedelta(days=7),
            duration_days=30,
            buy_fee=0.5,
            sell_fee=0.5,
        ),
    )
    assert competition is not None and not isinstance(competition, dict), "competition creation failed"
    context.competition_id = competition.id


@when("a new player requests entry into the competition with an empty CAPTCHA token")
def step_player_requests_entry(context):
    context.player_email = f"success+blackbox-player-{uuid.uuid4()}@simulator.amazonses.com"
    context.player_client = new_client(context)

    context.last_response = request_or_confirm_entry.sync_detailed(
        context.competition_id,
        client=context.player_client,
        body=EntryRequest(email=context.player_email, captcha_token=""),
    )


@then("the request is accepted")
def step_request_is_accepted(context):
    assert context.last_response.status_code == 202, context.last_response.content


@then("a registration link is sent to the player's e-mail")
def step_registration_link_sent(context):
    context.registration_link = last_email_link(context.api_base_url, context.player_email)
    assert "/login-links/" in context.registration_link


@when("the player follows the registration link and registers with a name")
def step_player_registers(context):
    token = _token_from_link(context.registration_link)

    still_needs_registration = consume_login_link.sync_detailed(token=token, client=context.player_client)
    assert still_needs_registration.status_code == 202, "expected a brand new player here"

    context.last_response = complete_registration.sync_detailed(
        token=token,
        client=context.player_client,
        body=CompleteRegistrationBody(name="Blackbox Test Player"),
    )
    assert context.last_response.status_code == 200, context.last_response.content


@then("the player is added to the competition")
def step_player_is_added(context):
    detail = get_competition_detail.sync(context.competition_id, client=context.player_client)
    assert detail is not None and not isinstance(detail, dict)
    assert detail.can_confirm_entry is False
    assert detail.access_level == AccessLevel.READ_WRITE
