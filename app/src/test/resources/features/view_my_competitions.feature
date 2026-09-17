Feature: View my competitions
  As a registered player
  I want to see the competitions I am involved in
  So that I can access them without searching through e-mails

  Rule: The player's competition list is grouped by their relationship to each competition

    Background:
      Given the user is a registered player
      And the user is logged into the system

    Scenario: Player views a competition they are currently participating in
      Given the player is registered in an open competition
      When they access their competitions list
      Then the system shows that competition under "participating"

    Scenario: Player views a competition they participated in that has since closed
      Given the player was registered in a competition that has since closed
      When they access their competitions list
      Then the system shows that competition under "participated in the past"

    Scenario: Player views a private competition they were invited to but have not confirmed
      Given the player was invited to a private competition
      And has not finished confirming entry yet
      When they access their competitions list
      Then the system shows that competition under "invited, not confirmed"

    Scenario: Player views a public competition they requested entry into but have not confirmed
      Given the player requested entry into a public competition
      And has not finished confirming entry yet
      When they access their competitions list
      Then the system shows that competition under "invited, not confirmed"

  Rule: Viewing a single competition's details depends on the player's relationship to it

    Background:
      Given the user is a registered player
      And the user is logged into the system

    Scenario: Player accesses the details of a competition they currently participate in
      Given the player is registered in an open competition
      When they access that competition's details
      Then the system shows the competition's basic information with read-write access

    Scenario: Player accesses the details of a competition they participated in that has closed
      Given the player was registered in a competition that has since closed
      When they access that competition's details
      Then the system shows the competition's basic information with read-only access

    Scenario: Player accesses the details of a competition they were invited to but have not confirmed
      Given the player was invited to a private competition
      And has not finished confirming entry yet
      When they access that competition's details
      Then the system shows the competition's basic information with read-only access
      And offers the option to confirm entry from that screen

    Scenario: Player confirms entry from the competition details screen after being invited
      Given the player was invited to a private competition
      And has not finished confirming entry yet
      When they confirm entry from that competition's details screen
      Then the system adds the player to the competition
      And the competition now shows read-write access

    Scenario: Player tries to access the details of a competition they have no relationship to
      Given the player never participated in or was invited to a competition
      When they try to access that competition's details
      Then the system shows an error message without revealing whether the competition exists

  Rule: The administrator can always view a competition's details, even without participating

    Background:
      Given the user is the system administrator
      And the user is logged into the system

    Scenario: Administrator accesses the details of a competition they did not join
      Given is not a participant in a competition
      When they access that competition's details
      Then the system shows the competition's basic information with read-only access
