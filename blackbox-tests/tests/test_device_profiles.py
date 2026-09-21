"""Technical checks for the fixed device profiles (spec 05-019) -- no HTTP, no running app
needed. Confirms the format DeviceLabelResolver (app/src/main/java/.../login/
DeviceLabelResolver.java) expects: Sec-CH-UA-Platform/-Platform-Version quoted, Sec-CH-UA-Mobile
as ?0/?1.
"""

import re

from common.device_profiles import ANDROID_MOBILE, WINDOWS_DESKTOP

_QUOTED = re.compile(r'^".*"$')


def test_windows_desktop_is_not_mobile():
    assert WINDOWS_DESKTOP.sec_ch_ua_mobile == "?0"


def test_android_mobile_is_mobile():
    assert ANDROID_MOBILE.sec_ch_ua_mobile == "?1"


def test_platform_and_platform_version_are_quoted():
    for profile in (WINDOWS_DESKTOP, ANDROID_MOBILE):
        assert _QUOTED.match(profile.sec_ch_ua_platform), profile.sec_ch_ua_platform
        assert _QUOTED.match(profile.sec_ch_ua_platform_version), profile.sec_ch_ua_platform_version


def test_the_two_profiles_have_different_platforms():
    assert WINDOWS_DESKTOP.sec_ch_ua_platform != ANDROID_MOBILE.sec_ch_ua_platform


def test_sec_ch_ua_includes_a_greased_brand_entry():
    # DeviceLabelResolver ignores any brand entry containing "not" (case-insensitive) --
    # both profiles need one present, same as a real browser would send.
    for profile in (WINDOWS_DESKTOP, ANDROID_MOBILE):
        assert "not" in profile.sec_ch_ua.lower()
