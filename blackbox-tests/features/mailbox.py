"""Spec 05-014's ``GET /blackbox/last-email`` -- the only way to read, from outside the Java
process, the link of an e-mail sent during a blackbox test (``POST /login-requests`` always
returns 202 with no body, and the link otherwise only lives in ``sent_email``).

Called directly via ``httpx``, never through the generated client: this route is test
scaffolding for the ``blackbox`` profile, deliberately kept out of ``docs/openapi.yaml`` so the
generated client keeps reflecting only the real product contract (see
specs/05-015-testes-blackbox-python/plan.md).
"""

import httpx


class NoEmailSentError(Exception):
    """Raised when no e-mail has ever been sent to the given address (404 from the endpoint)."""


def last_email_link(base_url: str, email: str) -> str:
    """Returns the link of the most recent e-mail sent to ``email``.

    Raises ``NoEmailSentError`` if nothing has been sent to that address yet.
    """
    response = httpx.get(f"{base_url}/blackbox/last-email", params={"email": email})
    if response.status_code == 404:
        raise NoEmailSentError(f"No e-mail sent to {email} yet")
    response.raise_for_status()
    return response.json()["link"]
