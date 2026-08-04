Feature: Create Competition
  As the system administrator
  I want to be able to create a new competition

  Background:
    Given the user is the system administrator
    And is logged into the system
    And is on the competition creation screen

  Scenario: Administrator successfully creates a public competition
    Given they choose the public competition option
    And define the start date
    And define the duration
    And define whether it is recurring or not
    And define the buy brokerage fee
    And define the sell brokerage fee
    When they click the "create" button
    Then the system should create a new public competition
    And show the creation success screen

  Scenario Outline: Administrator creates a private competition and decides on sending invites
    Given they choose the private competition option
    And enter a list of e-mails
    And define the start date
    And define the duration
    And define whether it is recurring or not
    And define the buy brokerage fee
    And define the sell brokerage fee
    And click the "create" button
    And the system creates the new private competition and shows the success screen asking whether to send the invite e-mails now or not
    When the administrator chooses <option>
    Then <result>

    Examples:
      | option              | result                                                                  |
      | send invites now    | the system sends the invite e-mails to the provided list               |
      | send invites later  | the system does not send the invite e-mails and keeps the competition awaiting sending |
