Feature: Browse public competitions
  As a player
  I want to see the list of public competitions open for entry
  So that I can choose one to join

  Scenario: Anyone browses the list of public competitions
    Given there are public competitions open for entry
    When they access the list of public competitions
    Then the system shows each competition's basic information

  Scenario: Logged-in player also sees the list of public competitions
    Given the player is registered and logged in
    And there are public competitions open for entry
    When they access the list of public competitions
    Then the system shows each competition's basic information

  Scenario: Closed public competitions do not appear in the list
    Given a public competition has already closed
    When they access the list of public competitions
    Then that competition is not shown

  Scenario: Private competitions never appear in the list
    Given a private competition exists
    When they access the list of public competitions
    Then that competition is not shown
