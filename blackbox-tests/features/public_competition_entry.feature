Feature: Public competition entry, from an external HTTP client's point of view

  Proof-of-concept end-to-end path for the blackbox environment (spec 05-014): a new player
  can join a public competition purely over HTTP, without solving any CAPTCHA challenge and
  without any way to inspect the application's internals other than the API itself -- reading
  e-mail links through GET /blackbox/last-email, the same as a real client would need a real
  mailbox for.

  Scenario: A new player requests entry, receives a registration link, and joins the competition
    Given the administrator is logged in via the magic link sent to "success+admin@simulator.amazonses.com"
    And the administrator creates a public competition
    When a new player requests entry into the competition with an empty CAPTCHA token
    Then the request is accepted
    And a registration link is sent to the player's e-mail
    When the player follows the registration link and registers with a name
    Then the player is added to the competition
