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
