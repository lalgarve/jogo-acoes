Feature: Manage competition players
  As the system administrator
  I want to manage the list of players in a competition
  So that I can track and fix invitation and registration issues

  Background:
    Given the user is the system administrator
    And the user is logged into the system
    And is on the player management screen for a competition

  Scenario Outline: Player list shows and can be filtered by status
    Given a player in the competition has status <status> since <date>
    Then the player list shows the player's status as <status> along with the date <date>
    When the administrator filters the player list by <status>
    Then only players with status <status> are shown

    Examples:
      | status                                      | date       |
      | e-mail not sent                              | 2026-08-01 |
      | e-mail sent but link not clicked             | 2026-08-01 |
      | link clicked but registration not finished   | 2026-08-02 |
      | in the competition                           | 2026-08-03 |

  Scenario: Administrator sends or resends the e-mail to a specific player
    Given a player is listed in the competition
    When the administrator chooses to send or resend the e-mail to that player
    Then the system sends the invite e-mail to the player
    And updates the e-mail sent date

  Scenario: Administrator sends or resends the e-mail to a group of players
    Given multiple players are listed in the competition
    When the administrator selects a group of players
    And chooses to send or resend the e-mail to the selected group
    Then the system sends the invite e-mail to each selected player
    And updates the e-mail sent date for each of them

  Scenario: Administrator edits a player's e-mail
    Given a player is listed in the competition
    When the administrator edits the player's e-mail
    And confirms the change
    Then the system updates the player's e-mail

  Scenario: Administrator tries to edit a player's e-mail to an invalid format
    Given a player is listed in the competition
    When the administrator edits the player's e-mail to an invalid e-mail address
    And confirms the change
    Then the system rejects the change and shows an error message

  Scenario: Administrator tries to edit a player's e-mail to one already used by another player in the competition
    Given a player is listed in the competition
    And another player in the competition already uses a given e-mail
    When the administrator edits the player's e-mail to that e-mail
    And confirms the change
    Then the system rejects the change and shows an error message

  Scenario: Administrator removes a player from the competition
    Given a player is listed in the competition
    When the administrator chooses to remove that player
    And confirms the removal
    Then the system removes the player from the competition
    And the player no longer appears in the player list

  Scenario: Administrator cancels a pending invite
    Given a player has status "e-mail sent but link not clicked" in the competition
    When the administrator chooses to cancel the invite
    And confirms the cancellation
    Then the system removes the player from the competition
    And the player no longer appears in the player list

  Scenario: Administrator invites new players to an existing private competition
    Given the competition is private
    When the administrator enters a new list of e-mails to invite
    And confirms the invitation
    Then the system adds the new players to the competition with status "e-mail not sent"
    And sends the invite e-mail to each of them

  Scenario: Administrator invites an e-mail that already belongs to a registered player
    Given the competition is private
    And one of the e-mails to invite already belongs to a registered player
    When the administrator enters that e-mail in the list to invite
    And confirms the invitation
    Then the system adds the player to the competition with status "e-mail not sent"
    And sends the e-mail personalized with the name instead of an invite to create an account
