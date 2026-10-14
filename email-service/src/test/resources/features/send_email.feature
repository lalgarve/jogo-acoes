Feature: Send an e-mail from a registered template
  As a client of the Serviço de E-mail
  I want to ask for an e-mail built from one of my own templates
  So that the e-mail goes out with my sender address without my code rendering or sending it

  Rule: A client can only send once operations has configured their sender address

    Background:
      Given a client authenticated with the API key "client-a"
      And they already registered a template named "welcome"

    Scenario: Client without a configured sender tries to send
      When they send the template "welcome" to "player@example.com"
      Then the system rejects the send because no sender address is configured
      And nothing is put on the send queue
      And no send is recorded

  Rule: A client sends an e-mail from their own template

    Background:
      Given a client authenticated with the API key "client-a"
      And operations configured the sender address "no-reply@client-a.example" for them
      And they already registered a template named "welcome"

    Scenario: Client sends an e-mail with template data
      When they send the template "welcome" to "player@example.com" with the variable "name" set to "Ada"
      Then the send is accepted as queued
      And the send queue has one message for "player@example.com" whose correlation id is the returned id
      And that message asks SES to send the template "welcome" of "client-a" from "no-reply@client-a.example"
      And that message carries the variable "name" set to "Ada"
      And the send is recorded under the returned id

    Scenario: Client sends an e-mail without template data
      When they send the template "welcome" to "player@example.com"
      Then the send is accepted as queued
      And the send queue has one message for "player@example.com" whose correlation id is the returned id
      And that message carries no variables

    Scenario: Client tries to send from a template that does not exist
      When they send the template "missing" to "player@example.com"
      Then the system shows an error that the template does not exist
      And nothing is put on the send queue

    Scenario: Client tries to send from another client's template
      Given another client authenticated with the API key "client-b" registered a template named "newsletter"
      When they send the template "newsletter" to "player@example.com"
      Then the system shows an error that the template does not exist
      And nothing is put on the send queue

    Scenario: Client sends a request without a template name
      When they send a request without a template name to "player@example.com"
      Then the system rejects the request as malformed
      And nothing is put on the send queue

    Scenario: Client sends a request without a recipient
      When they send a request for the template "welcome" without a recipient
      Then the system rejects the request as malformed
      And nothing is put on the send queue

    Scenario: Client sends a request with an invalid recipient address
      When they send the template "welcome" to "not-an-email"
      Then the system rejects the request as malformed
      And nothing is put on the send queue

    Scenario: The send queue is unavailable
      Given the send queue is unavailable
      When they send the template "welcome" to "player@example.com"
      Then the system answers that the service is unavailable
      And no send is recorded

  Rule: Every request requires an API key

    Scenario: Send request without an API key
      When a send request is made without an API key
      Then the system rejects the request as unauthorized
      And nothing is put on the send queue

  # Spec 05-047: checked against docker-compose.yml's test DNS server (docker/dns/), whose zone
  # has one .test name per outcome.
  Rule: The recipient's domain must accept e-mail

    Background:
      Given a client authenticated with the API key "client-a"
      And operations configured the sender address "no-reply@client-a.example" for them
      And they already registered a template named "welcome"

    Scenario Outline: A domain that accepts e-mail is approved and whitelisted
      When they send the template "welcome" to "player@<domain>"
      Then the send is accepted as queued
      And the domain "<domain>" is whitelisted as approved by "<record>"

      Examples:
        | domain         | record |
        | with-mx.test   | MX     |
        | only-a.test    | A      |
        | only-aaaa.test | AAAA   |

    Scenario: The domain is checked in lower case
      When they send the template "welcome" to "player@With-MX.Test"
      Then the send is accepted as queued
      And the domain "with-mx.test" is whitelisted as approved by "MX"

    Scenario: A whitelisted domain is accepted without a DNS lookup
      Given the domain "ghost.test" is already whitelisted
      When they send the template "welcome" to "player@ghost.test"
      Then the send is accepted as queued

    Scenario: The SES mailbox simulator is whitelisted from the start
      When they send the template "welcome" to "success@simulator.amazonses.com"
      Then the send is accepted as queued
      And the domain "simulator.amazonses.com" is whitelisted as approved by "SEED"

    Scenario Outline: A domain that does not accept e-mail is rejected
      When they send the template "welcome" to "player@<domain>"
      Then the system rejects the send because the domain "<domain>" does not accept e-mail
      And nothing is put on the send queue
      And no send is recorded
      And the domain "<domain>" is not whitelisted

      Examples:
        | domain       |
        | missing.test |
        | no-mail.test |
        | null-mx.test |

    Scenario: The DNS server does not answer
      When they send the template "welcome" to "player@unavailable.test"
      Then the send is accepted as queued
      And the domain "unavailable.test" is not whitelisted

  Rule: A disposable e-mail domain is rejected before the whitelist and DNS

    Background:
      Given a client authenticated with the API key "client-a"
      And operations configured the sender address "no-reply@client-a.example" for them
      And they already registered a template named "welcome"
      And the domain "disposable.test" is on an imported disposable domain list

    Scenario Outline: A disposable domain, or a domain under it, is rejected
      When they send the template "welcome" to "player@<domain>"
      Then the system rejects the send because the domain "<domain>" is a disposable e-mail domain
      And nothing is put on the send queue
      And no send is recorded
      And the domain "<domain>" is not whitelisted

      Examples:
        | domain              |
        | disposable.test     |
        | sub.disposable.test |

    Scenario: A disposable domain already on the whitelist is still rejected
      Given the domain "disposable.test" is already whitelisted
      When they send the template "welcome" to "player@disposable.test"
      Then the system rejects the send because the domain "disposable.test" is a disposable e-mail domain

    Scenario: A domain whose name only ends like a disposable one is not taken for it
      When they send the template "welcome" to "player@mydisposable.test"
      Then the system rejects the send because the domain "mydisposable.test" does not accept e-mail
