"""Technical, non-Gherkin check for the mailbox helper -- doesn't justify a full Scenario on
its own, but the rest of the suite silently depends on this behaving correctly (see
features/steps/public_competition_entry_steps.py).
"""

import os
import uuid

import pytest

from mailbox import NoEmailSentError, last_email_link

API_BASE_URL = os.environ.get("API_BASE_URL", "http://localhost:8080/api")


def test_last_email_link_raises_when_nothing_was_sent_to_the_address():
    never_used_email = f"success+never-sent-{uuid.uuid4()}@simulator.amazonses.com"

    with pytest.raises(NoEmailSentError):
        last_email_link(API_BASE_URL, never_used_email)
