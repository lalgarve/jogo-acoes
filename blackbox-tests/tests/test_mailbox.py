"""Technical, non-Gherkin check for the mailbox helper -- doesn't justify a full Scenario on
its own, but the rest of the suite silently depends on this behaving correctly (see
features/steps/public_competition_entry_steps.py).
"""

import os
import uuid

import httpx
import pytest

from common.blackbox_fixtures import (
    ADMIN_EMAIL,
    NoEmailSentError,
    _link_of,
    last_email,
    last_email_link,
    token_from_link,
)

API_BASE_URL = os.environ.get("API_BASE_URL", "http://localhost:8080/api")


def test_last_email_link_raises_when_nothing_was_sent_to_the_address():
    never_used_email = f"success+never-sent-{uuid.uuid4()}@simulator.amazonses.com"

    with pytest.raises(NoEmailSentError):
        last_email_link(never_used_email)


def test_last_email_only_consumes_the_message_it_read():
    """Reading an e-mail deletes only that one message from LocalStack's SES store (spec
    05-023) -- a second, independently sent e-mail to the same address is still there
    afterwards, not wiped out by the first read.
    """
    httpx.post(f"{API_BASE_URL}/login-requests", json={"email": ADMIN_EMAIL}).raise_for_status()
    httpx.post(f"{API_BASE_URL}/login-requests", json={"email": ADMIN_EMAIL}).raise_for_status()

    first = last_email(ADMIN_EMAIL)
    second = last_email(ADMIN_EMAIL)

    assert first.link != second.link


def test_link_comes_from_template_data_for_templated_emails():
    """Since spec 05-031 the e-mail is sent with SES `SendTemplatedEmail`, which LocalStack stores
    as `Template` + `TemplateData`, without a rendered `Body` (spec 05-040 found the helper still
    reading only `Body`)."""
    message = {"Template": "jogo-acoes__login-link", "TemplateData": '{"name": "A", "link": "/login-links/abc"}'}

    assert _link_of(message) == "/login-links/abc"


def test_link_still_comes_from_a_rendered_body():
    message = {"Body": {"html_part": '<a href="/login-links/xyz">entrar</a>'}}

    assert _link_of(message) == "/login-links/xyz"


def test_link_comes_from_template_data_when_absolute():
    """Spec 05-041: the link in the e-mail is an absolute URL."""
    link = "http://localhost:8080/api/login-links/abc"
    message = {"Template": "jogo-acoes__login-link", "TemplateData": f'{{"name": "A", "link": "{link}"}}'}

    assert _link_of(message) == link


@pytest.mark.parametrize(
    "link",
    [
        "http://localhost:8080/api/login-links/abc",
        "https://jogoacoes.leilaalgarve.de/api/login-links/abc/registration",
        "/login-links/abc",
    ],
)
def test_token_from_link_takes_the_segment_after_login_links(link):
    assert token_from_link(link) == "abc"
