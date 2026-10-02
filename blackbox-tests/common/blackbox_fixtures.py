"""Fixed facts about the `blackbox` environment (spec 05-014) shared by the Cucumber step
definitions (features/steps/) and the data seeder (seed/, spec 05-018): the seeded
administrator's e-mail, and reading the link (and metadata) of the most recent e-mail sent to a
given address.

`last_email`/`last_email_link` read LocalStack's own SES message store directly
(`GET {LOCALSTACK_URL}/_aws/ses`) -- there is no Java code in the loop (spec 05-023 removed
`BlackboxController`/`GET /blackbox/last-email`, the app-side endpoint this used to call): the
link only ever mattered to this Python suite, and LocalStack already exposes the full rendered
e-mail (`POST /login-requests` always returns 202 with no body, so this is the only way to see
the link at all). Reading a message deletes it (by its own id, never a blanket clear) so the
store doesn't grow without bound over a long test session, and concurrent test runs never
collide (every address is already unique per test -- spec 05-016).
"""

import datetime
import os
import re
import time
from dataclasses import dataclass

import httpx

ADMIN_EMAIL = "success+admin@simulator.amazonses.com"

LOCALSTACK_URL = os.environ.get("LOCALSTACK_URL", "http://localhost:4566")

# The real pipeline (app -> SQS -> Lambda event source mapping -> SES) is asynchronous --
# measured delivery delay ranges from under a second up to ~4s (worse on the Lambda's first,
# "cold" invocation). A single immediate read races this and fails almost every time, so
# `last_email` polls instead of reading once.
_POLL_INTERVAL_SECONDS = 0.3
_POLL_TIMEOUT_SECONDS = 10

# Every template that sends a link embeds it as <a href="...login-links...">. Matches whether
# the href is a relative path or a full absolute URL (Issue #92 tracks fixing which one it is).
_LINK_PATTERN = re.compile(r'href="([^"]*login-links[^"]*)"')


class NoEmailSentError(Exception):
    """Raised when no e-mail has ever been sent to the given address."""


@dataclass(frozen=True)
class LastEmail:
    link: str
    sent_at: datetime.datetime


def last_email(email: str) -> LastEmail:
    """Returns the most recent e-mail sent to ``email`` (link, and when it was sent -- used by
    the seeder, spec 05-018, to confirm the app is really running with the clock offset it was
    told about).

    Deletes the message from LocalStack's SES store before returning -- reading an e-mail
    consumes it, so a long test session doesn't accumulate messages forever. Safe under
    concurrent test runs: every address is already unique per test/scenario (spec 05-016), so
    the one message id this deletes could never belong to a different, concurrently-running
    test -- unlike a blanket `DELETE /_aws/ses` (no filter), which would also wipe out messages
    other tests haven't read yet.

    Polls LocalStack's SES store for up to ``_POLL_TIMEOUT_SECONDS`` before giving up -- the
    pipeline that actually delivers the e-mail is asynchronous, so it rarely shows up from the
    very first read.

    Raises ``NoEmailSentError`` if nothing has been sent to that address by the time the poll
    times out.
    """
    deadline = time.monotonic() + _POLL_TIMEOUT_SECONDS
    matches = []
    while True:
        response = httpx.get(f"{LOCALSTACK_URL}/_aws/ses")
        response.raise_for_status()
        # LocalStack's own ?email= filter matches the *sender*, not the recipient (this
        # project's sender is always the same address), so recipient filtering happens here
        # instead.
        matches = [
            message
            for message in response.json()["messages"]
            if email in message["Destination"]["ToAddresses"]
        ]
        if matches or time.monotonic() >= deadline:
            break
        time.sleep(_POLL_INTERVAL_SECONDS)

    if not matches:
        raise NoEmailSentError(f"No e-mail sent to {email} yet")

    latest = matches[-1]  # `messages` comes back in send order.
    html = latest["Body"]["html_part"] or ""
    link_match = _LINK_PATTERN.search(html)
    if link_match is None:
        raise RuntimeError(f"Could not find a login link in the e-mail sent to {email}")

    result = LastEmail(link=link_match.group(1), sent_at=datetime.datetime.fromisoformat(latest["Timestamp"]))
    httpx.delete(f"{LOCALSTACK_URL}/_aws/ses", params={"id": latest["Id"]}).raise_for_status()
    return result


def last_email_link(email: str) -> str:
    """Convenience wrapper over :func:`last_email` for callers that only need the link."""
    return last_email(email).link
