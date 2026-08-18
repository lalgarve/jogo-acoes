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
      When they click the "create" button
      Then the system should create a new public competition
      And show the creation success screen

    Scenario Outline: Administrator creates a private competition and decides on sending invites
      Given they choose the private competition option
      And click the "create" button
      And the system creates the new private competition and shows the success screen asking whether to send the invite e-mails now or not
      When the administrator decides to send the invite e-mails <timing>
      Then <result>

      Examples:
        | timing | result                                                                                 |
        | now    | the system sends the invite e-mails to the provided list                              |
        | later  | the system does not send the invite e-mails and keeps the competition awaiting sending |

    Scenario Outline: Administrator tries to create a competition with an invalid start date
      Given they choose the public competition option
      And define the start date as <start_date>
      When they click the "create" button
      Then the system rejects the competition creation and shows an error message about the invalid start date

      Examples:
        | start_date         |
        | a date in the past |

    Scenario Outline: Administrator tries to create a competition with an invalid duration
      Given they choose the public competition option
      And define the duration as <duration>
      When they click the "create" button
      Then the system rejects the competition creation and shows an error message about the invalid duration

      Examples:
        | duration |
        | 0 days   |

    Scenario Outline: Administrator tries to create a competition with an invalid buy brokerage fee
      Given they choose the public competition option
      And define the buy brokerage fee as <buy_fee>
      When they click the "create" button
      Then the system rejects the competition creation and shows an error message about the invalid brokerage fee

      Examples:
        | buy_fee |
        | -1%     |

    Scenario Outline: Administrator tries to create a competition with an invalid sell brokerage fee
      Given they choose the public competition option
      And define the sell brokerage fee as <sell_fee>
      When they click the "create" button
      Then the system rejects the competition creation and shows an error message about the invalid brokerage fee

      Examples:
        | sell_fee |
        | -1%      |

    Scenario: Administrator tries to create a competition without a name
      Given they choose the public competition option
      And leave the competition name empty
      When they click the "create" button
      Then the system rejects the competition creation and shows an error message about the missing name

    Scenario: Administrator tries to create a private competition without an e-mail list
      Given they choose the private competition option
      And leave the e-mail list empty
      When they click the "create" button
      Then the system rejects the competition creation and shows an error message about the missing e-mail list

    Scenario: Administrator tries to create a private competition with an invalid e-mail in the list
      Given they choose the private competition option
      And enter a list of e-mails containing an invalid e-mail address
      When they click the "create" button
      Then the system rejects the competition creation and shows an error message about the invalid e-mail

  Rule: Only the administrator can create competitions

    Scenario: Non-administrator tries to access the competition creation screen
      Given the user is logged into the system
      And is not the system administrator
      When they try to access the competition creation screen
      Then the system denies access and shows an error message
