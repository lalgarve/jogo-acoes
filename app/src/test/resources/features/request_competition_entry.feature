Feature: Request entry into a public competition
  As a player
  I want to request entry into a competition

  Scenario: Unregistered player requests entry into a public competition
    Given the player is unregistered
    And clicked on Join a Competition
    And clicked on a public competition
    And entered a valid e-mail
    And passed the not-a-robot test
    Then the system adds the e-mail to the list of requesters and sends the link to finish registration by e-mail

  Scenario: Registered but not logged in player requests entry into a public competition
    Given the player is registered and not logged in
    And clicked on Join a Competition
    And clicked on a public competition
    And entered a valid e-mail
    And passed the not-a-robot test
    Then the system adds the e-mail personalized with the name to the list of requesters and sends the link to finish registration by e-mail

  Scenario: Registered and logged in player requests entry into a public competition
    Given the player is registered and logged in
    And clicked on Join a Competition
    And clicked on a public competition
    And confirmed entry into the competition
    Then the system adds the player to the public competition and redirects them to the competition page

  Scenario: Player tries to request entry into a private competition
    Given the player is registered and logged in
    And clicked on Join a Competition
    When they try to access a private competition they were not invited to
    Then the system does not allow the request and does not show the private competition in the list

  Scenario: Player enters an invalid e-mail while requesting entry into a public competition
    Given the player is unregistered
    And clicked on Join a Competition
    And clicked on a public competition
    When they enter an invalid e-mail address
    Then the system shows an error message and does not send the registration link

  Scenario: Player fails the not-a-robot test while requesting entry into a public competition
    Given the player is unregistered
    And clicked on Join a Competition
    And clicked on a public competition
    And entered a valid e-mail
    When they fail the not-a-robot test
    Then the system shows an error message and does not send the registration link

  Scenario: Player requests entry again into a public competition they already requested
    Given the player already requested entry into a public competition
    And has not finished registration yet
    When they request entry into that same competition again
    Then the system resends the link to finish registration by e-mail

  Scenario: Registered player confirms entry into a competition they already joined
    Given a registered player already participates in a public competition
    When they confirm entry into that competition again
    Then their entry date does not change
    And no new participation status change is recorded

  Scenario: Player requests entry twice and uses the first link
    Given the player is unregistered
    And requested entry into a public competition twice
    And both e-mails contain the same link
    When they open the link from the first e-mail
    Then they can finish registration and join the competition

  Scenario: Player requests entry again when the link expires within a day
    Given the player is unregistered
    And requested entry into a public competition
    And that link expires in less than a day
    When they request entry into that competition again
    Then they receive a new link by e-mail
    And the link from the first e-mail still works until it expires
