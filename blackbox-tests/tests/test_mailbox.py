"""Technical, non-Gherkin check for the mailbox helper -- doesn't justify a full Scenario on
its own, but the rest of the suite silently depends on this behaving correctly (see
features/steps/public_competition_entry_steps.py).
"""

import os
import uuid

import httpx
import pytest

from common.blackbox_fixtures import ADMIN_EMAIL, NoEmailSentError, last_email, last_email_link

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
