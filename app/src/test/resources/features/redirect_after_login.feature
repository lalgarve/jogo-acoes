Feature: Redirect to the originally requested page after login
  As a player who is not logged in
  I want to be sent back to the page I was trying to reach after I log in
  So that I do not have to navigate there again manually

  Scenario: Player tries to access a competition's details without being logged in
    Given the player is registered and not logged in
    When they try to access a specific competition's details
    Then the system sends them a login link by e-mail
    And clicking that link logs them in and takes them straight to that competition's details

  Scenario: Player tries to access their competitions list without being logged in
    Given the player is registered and not logged in
    When they try to access the list of their own competitions
    Then the system sends them a login link by e-mail
    And clicking that link logs them in and takes them straight to their competitions list

  Scenario: A newer login link supersedes an earlier one with a different destination
    Given the player requested a login link while trying to reach one competition's details
    And then requested another login link while trying to reach their competitions list
    When they click the newer link
    Then it logs them in and takes them straight to their competitions list, not the earlier destination
