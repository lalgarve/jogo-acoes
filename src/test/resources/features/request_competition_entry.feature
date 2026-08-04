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
