Feature: Manage active sessions

  Scenario: Player logs in on a second device and sees two distinct labels
    Given a registered player, already logged in on a "Windows desktop" device
    When they also log in on an "Android mobile" device
    And they list their active sessions
    Then the system shows two sessions with distinct device labels
