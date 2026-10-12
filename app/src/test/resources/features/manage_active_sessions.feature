Feature: Manage active sessions
  As a logged-in player
  I want to see and control my own active devices
  So that I can end a session I no longer trust, even from another device

  Rule: A logged-in player controls their own sessions

    Background:
      Given the user is a registered player
      And the user is logged into the system

    Scenario: Player lists their active sessions
      When they list their active sessions
      Then the system shows one session, marked as the current one

    Scenario: Player logs in on a second device and sees two distinct, recognizable labels
      Given they also log in on a second device sending User-Agent Client Hints for "Windows" and browser "Chromium"
      When they list their active sessions
      Then the system shows two sessions with distinct device labels

    Scenario: Player revokes a session from another device
      Given they also log in on a second device sending User-Agent Client Hints for "Windows" and browser "Chromium"
      When they revoke the other device's session
      Then that device's session no longer works for authenticated requests

    Scenario: Player revokes their own current session
      When they revoke their own current session
      Then they are logged out of the current device

    Scenario: Player tries to revoke a session that does not exist
      When they try to revoke a session that does not exist
      Then the system shows an error message without revealing whether the session exists

    Scenario: Player tries to revoke another player's session
      Given another registered player has an active session
      When they try to revoke that other player's session
      Then the system shows an error message without revealing whether the session exists

    Scenario: Player logs out of the current device
      Given they also log in on a second device sending User-Agent Client Hints for "Windows" and browser "Chromium"
      When they log out of the current session
      Then they are logged out of the current device
      And the second device's session still works for authenticated requests

    Scenario: Logged-out session no longer appears in the active sessions list
      Given they also log in on a second device sending User-Agent Client Hints for "Windows" and browser "Chromium"
      When they log out of the current session
      And they list their active sessions from the second device
      Then the system shows one session, marked as the current one

  Rule: Logging out requires being logged in

    Scenario: Anonymous user tries to log out
      Given the user is not logged in
      When they try to log out of the current session
      Then the system rejects the request as not logged in
