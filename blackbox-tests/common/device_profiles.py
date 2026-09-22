"""Fixed device profiles (User-Agent Client Hints, spec 05-009) for tests that need
`GET /sessions`'s `deviceLabel` to be something other than the generic `httpx` fallback --
`seed/` (spec 05-018) and `features/steps/`. Values match the ones already calibrated against
`DeviceLabelResolver` by the Java suite (`DeviceIdentificationSteps.java`,
`ManageActiveSessionsSteps.java`, `DeviceLabelResolverTest.java`), reused here instead of
picking new ones, so both suites stay consistent with the same parser.
"""

from dataclasses import dataclass


@dataclass(frozen=True)
class DeviceProfile:
    sec_ch_ua: str
    sec_ch_ua_platform: str
    sec_ch_ua_platform_version: str
    sec_ch_ua_mobile: str


# The "Not_A Brand" entry is the Client Hints spec's mandatory "greased" brand -- every real
# browser sends one, and DeviceLabelResolver already ignores it (any brand containing "not").
_CHROMIUM_SEC_CH_UA = '"Not_A Brand";v="24", "Chromium";v="131"'

WINDOWS_DESKTOP = DeviceProfile(
    sec_ch_ua=_CHROMIUM_SEC_CH_UA,
    sec_ch_ua_platform='"Windows"',
    sec_ch_ua_platform_version='"15.0.0"',
    sec_ch_ua_mobile="?0",
)

ANDROID_MOBILE = DeviceProfile(
    sec_ch_ua=_CHROMIUM_SEC_CH_UA,
    sec_ch_ua_platform='"Android"',
    sec_ch_ua_platform_version='"14"',
    sec_ch_ua_mobile="?1",
)
