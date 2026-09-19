"""Shared behave hook -- reads API_BASE_URL (default: the `blackbox` environment's usual
address, spec 05-014) and gives every step definition a way to create a fresh HTTP "device"
against it, matching how the Java Cucumber suite's ScenarioWorld hands out one RestAssured
session per device (see specs/05-015-testes-blackbox-python/plan.md).
"""

import os

from jogo_acoes_client import Client


def before_all(context):
    context.api_base_url = os.environ.get("API_BASE_URL", "http://localhost:8080/api")


def new_client(context):
    """A fresh Client -- its own cookie jar, so it behaves like a brand new browser/device.

    The generated Client keeps one httpx.Client internally and reuses it across calls, so
    session cookies set by the API (e.g. after consuming a login link) persist automatically
    for whoever holds this same instance -- exactly what a scenario needs to act as "the
    administrator" or "the player" across several requests.
    """
    return Client(base_url=context.api_base_url)
