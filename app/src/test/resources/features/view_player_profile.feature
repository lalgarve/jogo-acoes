Feature: View player profile
  As a player or administrator
  I want to see a player's summary
  So that I know who they are and how many competitions they are involved in

  Rule: A player sees only their own profile

    Background:
      Given the user is a registered player

    Scenario: Player views their own profile
      Given they joined 2 public competitions and 1 private competition
      And the user is logged into the system
      When they view their own profile
      Then the system shows their name and e-mail
      And shows 0 owned, 2 public and 1 private competitions

    Scenario: Pending invitations are not counted
      Given they joined 1 private competition
      And they were invited to another private competition and did not confirm entry
      And the user is logged into the system
      When they view their own profile
      Then the system shows 0 owned, 0 public and 1 private competitions

    Scenario: Player tries to view another player's profile
      Given another registered player exists
      And the user is logged into the system
      When they try to view that other player's profile
      Then the system shows an error message without revealing whether the player exists

    Scenario: Player tries to look up a player by e-mail
      Given another registered player exists
      And the user is logged into the system
      When they try to look up that other player by e-mail
      Then the system denies access

  Rule: The administrator sees any player's profile

    Background:
      Given the user is the system administrator

    Scenario: Administrator views any player's profile
      Given a registered player joined 1 public competition
      And the user is logged into the system
      When they view that player's profile
      Then the system shows 0 owned, 1 public and 0 private competitions

    Scenario: Administrator's own profile counts the competitions they created
      Given they created 3 competitions
      And the user is logged into the system
      When they view their own profile
      Then the system shows 3 owned competitions

    Scenario: Administrator views a profile that does not exist
      Given the user is logged into the system
      When they try to view the profile of a player that does not exist
      Then the system reports that the player was not found

    Scenario: Administrator looks up a player by e-mail, regardless of letter case
      Given a registered player joined 1 public competition
      And the user is logged into the system
      When they look up that player by e-mail written in upper case
      Then the system shows that player's summary

    Scenario: Administrator looks up an e-mail that belongs to no player
      Given the user is logged into the system
      When they look up an e-mail that belongs to no player
      Then the system reports that the player was not found

    Scenario: Administrator looks up a malformed e-mail
      Given the user is logged into the system
      When they look up the e-mail "not-an-email"
      Then the system rejects the request as invalid

    Scenario: Administrator looks up a player without giving an e-mail
      Given the user is logged into the system
      When they look up a player without giving an e-mail
      Then the system rejects the request as invalid

  Rule: Viewing a profile requires being logged in

    Scenario: Anonymous user tries to view a profile
      Given the user is not logged in
      When they try to view a player's profile
      Then the system rejects the request as not logged in
