Feature: Create Competition
  As the system administrator
  I want to be able to create a new competition

  Rule: The administrator defines the competition and creates it

    Background:
      Given the user is the system administrator
      And the user is logged into the system
      And is on the competition creation screen

    Scenario: Administrator successfully creates a public competition
      Given they choose the public competition option
      And define the competition name
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
      And define the competition name
      And enter a list of e-mails
      And define the start date
      And define the duration
      And define whether it is recurring or not
      And define the buy brokerage fee
      And define the sell brokerage fee
      And click the "create" button
      And the system creates the new private competition and shows the success screen asking whether to send the invite e-mails now or not
      When the administrator decides to send the invite e-mails <timing>
      Then <result>

      Examples:
        | timing | result                                                                                 |
        | now    | the system sends the invite e-mails to the provided list                              |
        | later  | the system does not send the invite e-mails and keeps the competition awaiting sending |

    Scenario Outline: Administrator tries to create a competition with invalid data
      Given they choose the public competition option
      And define the competition name
      And define the start date as <start_date>
      And define the duration as <duration>
      And define the buy brokerage fee as <buy_fee>
      And define the sell brokerage fee as <sell_fee>
      When they click the "create" button
      Then the system rejects the competition creation and shows an error message about the <error>

      Examples:
        | start_date          | duration | buy_fee | sell_fee | error                  |
        | a date in the past  | 5 days   | 0.5%    | 0.5%     | invalid start date     |
        | a valid future date | 0 days   | 0.5%    | 0.5%     | invalid duration       |
        | a valid future date | 5 days   | -1%     | 0.5%     | invalid brokerage fee  |
        | a valid future date | 5 days   | 0.5%    | -1%      | invalid brokerage fee  |

    Scenario: Administrator tries to create a competition without a name
      Given they choose the public competition option
      And leave the competition name empty
      And define the start date
      And define the duration
      And define the buy brokerage fee
      And define the sell brokerage fee
      When they click the "create" button
      Then the system rejects the competition creation and shows an error message about the missing name

    Scenario: Administrator tries to create a private competition without an e-mail list
      Given they choose the private competition option
      And define the competition name
      And leave the e-mail list empty
      And define the start date
      And define the duration
      And define the buy brokerage fee
      And define the sell brokerage fee
      When they click the "create" button
      Then the system rejects the competition creation and shows an error message about the missing e-mail list

    Scenario: Administrator tries to create a private competition with an invalid e-mail in the list
      Given they choose the private competition option
      And define the competition name
      And enter a list of e-mails containing an invalid e-mail address
      And define the start date
      And define the duration
      And define the buy brokerage fee
      And define the sell brokerage fee
      When they click the "create" button
      Then the system rejects the competition creation and shows an error message about the invalid e-mail

  Rule: Only the administrator can create competitions

    Scenario: Non-administrator tries to access the competition creation screen
      Given the user is logged into the system
      And is not the system administrator
      When they try to access the competition creation screen
      Then the system denies access and shows an error message
