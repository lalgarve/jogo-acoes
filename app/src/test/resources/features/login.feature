Feature: Login via link sent by e-mail
  As a system user
  I want to log in through a link received by e-mail
  So that I can access the system without a password

  Scenario Outline: New player logs in and confirms entry into the competition
    Given the user is a new player
    And <situation>
    And clicked the login link received by e-mail
    When they enter their name
    And confirm entry into the competition
    Then the system registers the player
    And includes them in the competition
    And redirects them to the competition page

    Examples:
      | situation                                       |
      | requested entry into a new public competition   |
      | was invited to join a private competition       |

  Scenario Outline: Registered player confirms entry into a competition after requesting or being invited
    Given the user is a registered player
    And <situation>
    And clicked the login link received by e-mail
    When they confirm entry into the competition
    Then the system adds the player to the competition
    And redirects them to the competition page

    Examples:
      | situation                                       |
      | requested entry into a new public competition   |
      | was invited to join a private competition       |

  Scenario: Registered player requests entry again into a competition they already participate in
    Given the user is a registered player
    And already participates in a public competition
    And requested entry into that competition again
    When they click the login link received by e-mail
    Then the system redirects them directly to the competition page

  Scenario: Player who participated in a closed competition accesses the link and sees the final result
    Given the user is a registered player
    And the competition referenced in the link is already closed
    And the player participated in that competition
    When they click the login link received by e-mail
    Then the system redirects them to the competition page
    And shows the final result

  Scenario Outline: Player with unfinished registration accesses the link of a closed competition and gets an error
    Given the player's status is <status>
    And the competition referenced in the link is already closed
    When they click the login link received by e-mail
    Then the system shows an error message

    Examples:
      | status                                                          |
      | requested entry into a public competition and did not finish registration |
      | was invited to a private competition and did not finish registration      |

  Scenario: Registered player requests the login link
    Given the user is a registered player
    And requested the login link
    When they click the login link received by e-mail
    Then the system redirects them to the page listing the competitions they are participating in or have participated in

  Scenario: Administrator requests the login link
    Given the user is the system administrator
    And requested the login link
    When they click the login link received by e-mail
    Then the system redirects them to the administration page

  Scenario: Player accesses an invalid or expired login link
    Given the user clicked a login link that is invalid or expired
    When the system validates the link
    Then the system shows an error message
    And offers the option to request a new login link

  Scenario: Player tries to use a login link that was already used on another device
    Given a registered player used the login link to log in on one device
    And they are not logged in on this other device
    When they click the same login link on this other device
    Then the system shows an error message

  Scenario: Already logged-in player clicks a login link originally used on a different device
    Given the player is already logged in on this device
    And the login link was already used to log in on a different device
    When they click the login link received by e-mail
    Then the system redirects them to the competition page

  Scenario: Requesting a new login link invalidates the previous one
    Given a registered player has an active login link that has not been used yet
    When they request a new login link
    Then the previous login link is invalidated
    And only the new login link can be used to log in

  Scenario: Player logs in on a new device beyond the configured limit
    Given the player is already logged in on the maximum number of devices allowed by the system
    When they log in successfully on one more device
    Then the system ends the oldest active session
    And the player remains within the configured device limit
