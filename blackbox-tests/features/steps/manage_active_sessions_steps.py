import datetime
import uuid

from behave import given, when, then

from common.blackbox_fixtures import ADMIN_EMAIL, last_email_link
from common.device_profiles import ANDROID_MOBILE, DeviceProfile, WINDOWS_DESKTOP
from environment import new_client

from jogo_acoes_client.api.competitions import create_competition
from jogo_acoes_client.api.entry_requests import request_or_confirm_entry
from jogo_acoes_client.api.login import complete_registration, consume_login_link, request_login_link
from jogo_acoes_client.api.sessions import list_active_sessions
from jogo_acoes_client.models import (
    CompetitionCreateRequest,
    CompetitionType,
    CompleteRegistrationBody,
    EntryRequest,
    RequestLoginLinkBody,
)


def _token_from_link(link: str) -> str:
    return link.strip("/").split("/")[1]


def _consume_login_link_expect_registered(client, token, device: DeviceProfile):
    """Consumes a link for an account that's already registered -- 200 with a LoginResult."""
    login_result = consume_login_link.sync(
        client=client,
        token=token,
        sec_ch_ua=device.sec_ch_ua,
        sec_ch_ua_platform=device.sec_ch_ua_platform,
        sec_ch_ua_platform_version=device.sec_ch_ua_platform_version,
        sec_ch_ua_mobile=device.sec_ch_ua_mobile,
    )
    assert login_result is not None, f"expected {token} to already be a registered account"
    return login_result


def _consume_login_link_expect_new_player(client, token, device: DeviceProfile):
    """Consumes a link for a brand new player -- 202, no body, registration still pending."""
    response = consume_login_link.sync_detailed(
        client=client,
        token=token,
        sec_ch_ua=device.sec_ch_ua,
        sec_ch_ua_platform=device.sec_ch_ua_platform,
        sec_ch_ua_platform_version=device.sec_ch_ua_platform_version,
        sec_ch_ua_mobile=device.sec_ch_ua_mobile,
    )
    assert response.status_code == 202, f"expected a brand new player here, got {response.status_code}"


@given('a registered player, already logged in on a "Windows desktop" device')
def step_registered_player_logged_in(context):
    admin_client = new_client(context)
    admin_response = request_login_link.sync_detailed(client=admin_client, body=RequestLoginLinkBody(email=ADMIN_EMAIL))
    assert admin_response.status_code == 202, admin_response.content
    admin_link = last_email_link(context.api_base_url, ADMIN_EMAIL)
    _consume_login_link_expect_registered(admin_client, _token_from_link(admin_link), WINDOWS_DESKTOP)

    competition = create_competition.sync(
        client=admin_client,
        body=CompetitionCreateRequest(
            name=f"Blackbox sessions cup {uuid.uuid4()}",
            type_=CompetitionType.PUBLIC,
            start_date=datetime.date.today() + datetime.timedelta(days=7),
            duration_days=30,
            buy_fee=0.5,
            sell_fee=0.5,
        ),
    )
    assert competition is not None and not isinstance(competition, dict), "competition creation failed"

    context.player_email = f"success+blackbox-sessions-{uuid.uuid4()}@simulator.amazonses.com"
    context.player_client = new_client(context)
    entry_response = request_or_confirm_entry.sync_detailed(
        competition.id, client=context.player_client, body=EntryRequest(email=context.player_email, captcha_token="")
    )
    assert entry_response.status_code == 202, entry_response.content

    registration_link = last_email_link(context.api_base_url, context.player_email)
    token = _token_from_link(registration_link)
    _consume_login_link_expect_new_player(context.player_client, token, WINDOWS_DESKTOP)

    registration = complete_registration.sync_detailed(
        token=token,
        client=context.player_client,
        body=CompleteRegistrationBody(name="Blackbox Sessions Player"),
        sec_ch_ua=WINDOWS_DESKTOP.sec_ch_ua,
        sec_ch_ua_platform=WINDOWS_DESKTOP.sec_ch_ua_platform,
        sec_ch_ua_platform_version=WINDOWS_DESKTOP.sec_ch_ua_platform_version,
        sec_ch_ua_mobile=WINDOWS_DESKTOP.sec_ch_ua_mobile,
    )
    assert registration.status_code == 200, registration.content


@when('they also log in on an "Android mobile" device')
def step_log_in_on_second_device(context):
    context.second_device_client = new_client(context)
    response = request_login_link.sync_detailed(
        client=context.second_device_client, body=RequestLoginLinkBody(email=context.player_email)
    )
    assert response.status_code == 202, response.content

    link = last_email_link(context.api_base_url, context.player_email)
    _consume_login_link_expect_registered(context.second_device_client, _token_from_link(link), ANDROID_MOBILE)


@when("they list their active sessions")
def step_list_active_sessions(context):
    context.sessions = list_active_sessions.sync(client=context.second_device_client)
    assert context.sessions is not None and not isinstance(context.sessions, dict), context.sessions


@then("the system shows two sessions with distinct device labels")
def step_two_distinct_labels(context):
    assert len(context.sessions) == 2, f"expected 2 sessions, got {len(context.sessions)}: {context.sessions}"
    labels = {session.device_label for session in context.sessions}
    assert len(labels) == 2, f"expected 2 distinct device labels, got {labels}"
