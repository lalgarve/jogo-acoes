"""Technical checks for the data generator's factories and catalog data (spec 05-018) -- no
HTTP, no running app needed. `seed/flows.py`/`seed/__main__.py`'s actual HTTP orchestration is
only exercised by the manual end-to-end run against a real blackbox environment (see
specs/05-018-gerador-dados-teste/tasks.md, T013).
"""

import datetime

import pytest
from jogo_acoes_client.models import CompetitionType

from seed import factories
from seed.__main__ import parse_args
from seed.profiles import CATALOG, PROFILES, VOLUME_PLAYER_COUNT

START_DATE = datetime.date(2026, 1, 1)


def test_public_competition_request_has_valid_defaults():
    request = factories.public_competition_request("Copa", START_DATE)

    assert request.name == "Copa"
    assert request.type_ == CompetitionType.PUBLIC
    assert request.start_date == START_DATE
    assert request.duration_days == 30
    assert request.recurring is False


def test_public_competition_request_overrides_one_field_at_a_time():
    request = factories.public_competition_request("Copa", START_DATE, duration_days=60)

    assert request.duration_days == 60
    assert request.name == "Copa"  # everything else stays at its default


def test_private_competition_request_carries_the_invited_emails():
    request = factories.private_competition_request("Copa", START_DATE, ["a@example.com", "b@example.com"])

    assert request.type_ == CompetitionType.PRIVATE
    assert request.emails == ["a@example.com", "b@example.com"]


def test_invite_timing_request_accepts_now_and_later():
    assert factories.invite_timing_request("now").timing.value == "now"
    assert factories.invite_timing_request("later").timing.value == "later"


@pytest.mark.parametrize("profile", ["minimal", "standard", "volume"])
def test_every_profile_only_references_known_competitions(profile):
    for code in PROFILES[profile]:
        assert code in CATALOG or code == "V1", f"{code} in profile {profile!r} has no catalog entry"


def test_minimal_profile_is_a_small_subset_of_standard():
    assert set(PROFILES["minimal"]) < set(PROFILES["standard"])


def test_standard_profile_covers_the_whole_catalog():
    assert set(PROFILES["standard"]) == set(CATALOG.keys())


def test_volume_profile_is_standard_plus_one_more_competition():
    assert set(PROFILES["volume"]) == set(PROFILES["standard"]) | {"V1"}


def test_volume_player_count_is_two_hundred():
    assert VOLUME_PLAYER_COUNT == 200


def test_c8_reuses_c2_players_instead_of_fresh_addresses():
    assert CATALOG["C8"].invited_are_preexisting_players is True
    assert CATALOG["C2"].invited_are_preexisting_players is False


@pytest.mark.parametrize("offset", [0, 1, 30])
def test_clock_offset_days_rejects_zero_and_positive(offset):
    with pytest.raises(SystemExit):
        parse_args(["--clock-offset-days", str(offset)])


def test_clock_offset_days_accepts_negative():
    args = parse_args(["--clock-offset-days", "-15"])
    assert args.clock_offset_days == -15
    assert args.profile == "standard"
