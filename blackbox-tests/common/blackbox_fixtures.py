"""Fixed facts about the `blackbox` environment (spec 05-014) shared by the Cucumber step
definitions (features/steps/) and the data seeder (seed/, spec 05-018): the seeded
administrator's e-mail, and reading `GET /blackbox/last-email` -- the only way, outside the Java
process, to read the link (and metadata) of an e-mail sent during a blackbox test (`POST
/login-requests` always returns 202 with no body, and the link otherwise only lives in
`sent_email`).

Called directly via `httpx`, never through the generated client: this route is test
scaffolding for the `blackbox` profile, deliberately kept out of `docs/openapi.yaml` so the
generated client keeps reflecting only the real product contract (see
specs/05-015-testes-blackbox-python/plan.md).
"""

import datetime
from dataclasses import dataclass

import httpx

ADMIN_EMAIL = "success+admin@simulator.amazonses.com"


class NoEmailSentError(Exception):
    """Raised when no e-mail has ever been sent to the given address (404 from the endpoint)."""


@dataclass(frozen=True)
class LastEmail:
    link: str
    template: str
    sent_at: datetime.datetime


def last_email(base_url: str, email: str) -> LastEmail:
    """Returns the most recent e-mail sent to ``email`` (link, template, and when the app's own
    clock sent it -- used by the seeder, spec 05-018, to confirm the app is really running with
    the clock offset it was told about).

    Raises ``NoEmailSentError`` if nothing has been sent to that address yet.
    """
    response = httpx.get(f"{base_url}/blackbox/last-email", params={"email": email})
    if response.status_code == 404:
        raise NoEmailSentError(f"No e-mail sent to {email} yet")
    response.raise_for_status()
    body = response.json()
    return LastEmail(
        link=body["link"],
        template=body["template"],
        sent_at=datetime.datetime.fromisoformat(body["sentAt"]),
    )


def last_email_link(base_url: str, email: str) -> str:
    """Convenience wrapper over :func:`last_email` for callers that only need the link."""
    return last_email(base_url, email).link
