Feature: Search players
  As the administrator
  I want to filter the list of players
  So that I can find players by competition type, e-mail domain or ownership

  Rule: The administrator searches players with filters and pages

    Background:
      Given the user is the system administrator
      And the following players exist, in competitions created by the administrator:
        | name  | email domain | public | private |
        | Ana   | main         | 1      | 0       |
        | Bruno | main         | 0      | 2       |
        | Carla | other        | 1      | 1       |
        | Davi  | MAIN         | 0      | 0       |
      And the user is logged into the system

    Scenario: Search without filters returns every user
      When they search players with no filter
      Then the result total is the number of users in the system
      And the result has the default page size of players, or fewer if there are fewer users
      And the result contains "Ana", "Bruno", "Carla", "Davi" and the administrator

    Scenario Outline: Search by a single filter
      When they search players filtering by <filter>
      Then among this scenario's users, the result contains exactly <names>

      Examples:
        | filter                   | names                  |
        | competition type PUBLIC  | "Ana", "Carla"         |
        | competition type PRIVATE | "Bruno", "Carla"       |
        | the "main" e-mail domain | "Ana", "Bruno", "Davi" |

    Scenario: Filters are combined
      When they search players filtering by competition type PUBLIC and the "main" e-mail domain
      Then the result contains exactly "Ana"

    Scenario: Search by competition owners
      When they search players filtering by owners only
      Then among this scenario's users, the result contains exactly the administrator

    Scenario: Search by players who own no competition
      When they search players filtering by non-owners only
      Then among this scenario's users, the result contains exactly "Ana", "Bruno", "Carla", "Davi"

    Scenario: Long result is split into pages
      Given 25 additional players with the "bulk" e-mail domain exist
      When they search players filtering by the "bulk" e-mail domain with page size 10
      Then the result total is 25
      And the result has 10 players
      And requesting pages 0, 1 and 2 returns every "bulk" player exactly once, ordered by name

    Scenario: Page beyond the end
      When they search players filtering by the "main" e-mail domain on page 5
      Then the result has no players
      And the result total is 3

    Scenario Outline: Invalid search
      When they search players with <invalid input>
      Then the system rejects the request as invalid

      Examples:
        | invalid input              |
        | page size 0                |
        | page size 500              |
        | page -1                    |
        | e-mail domain ""           |
        | e-mail domain "a@acme.com" |
        | competition type "HYBRID"  |

  Rule: Only the administrator searches players

    Scenario: Player tries to search players
      Given the user is a registered player
      And the user is logged into the system
      When they try to search players
      Then the system denies access

    Scenario: Anonymous user tries to search players
      Given the user is not logged in
      When they try to search players
      Then the system rejects the request as not logged in
