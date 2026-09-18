Feature: Device identification for login sessions
  As the system
  I want to label each login session with a readable device description
  So that a player can later recognize which device is which

  Scenario: Player logs in with User-Agent Client Hints present
    Given a registered player has an active login link
    When they click the login link sending User-Agent Client Hints for "Windows" "15.0.0" and browser "Chromium" "131"
    Then the session recorded for that login shows the device as "Windows 15.0.0 · Chromium 131"

  Scenario: Player logs in with only a traditional User-Agent
    Given a registered player has an active login link
    When they click the login link sending only a traditional User-Agent header
    Then the session recorded for that login shows the raw User-Agent as the device

  Scenario: Player logs in with no device-identifying header at all
    Given a registered player has an active login link
    When they click the login link without any device-identifying header
    Then the session recorded for that login shows the device as "unknown-device"
