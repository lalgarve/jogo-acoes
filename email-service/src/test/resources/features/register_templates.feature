Feature: Register e-mail templates
  As a client of the Serviço de E-mail
  I want to register and preview the e-mail templates I use
  So that I can change e-mail content without a code deploy, and know a template is valid before relying on it

  Rule: A client registers a new template

    Background:
      Given a client authenticated with the API key "client-a"

    Scenario: Client registers a valid template
      When they register a template named "welcome" with subject "Welcome!" and a valid body
      Then the template is created
      And the template is synced to SES

    @requires-real-ses
    Scenario: Client registers a template with invalid syntax
      When they register a template named "broken" with an invalid Handlebars body
      Then the system rejects the registration with the reason SES returned
      And no template named "broken" is created

    Scenario: Client registers a template with a name they already used
      Given they already registered a template named "welcome"
      When they register another template named "welcome"
      Then the system rejects the registration because the name is already in use

  Rule: A client updates an existing template

    Background:
      Given a client authenticated with the API key "client-a"

    Scenario: Client updates their own template
      Given they already registered a template named "welcome"
      When they update the template named "welcome" with a new subject and body
      Then the template reflects the new content
      And the updated template is synced to SES

    Scenario: Client tries to update a template that does not exist
      When they try to update a template named "missing"
      Then the system shows an error that the template does not exist

    @requires-real-ses
    Scenario: Client registers a template, then updates it with invalid syntax
      Given they already registered a template named "welcome"
      When they update the template named "welcome" with an invalid Handlebars body
      Then the system rejects the update with the reason SES returned
      And the template named "welcome" keeps its previous content

  Rule: A client previews a template without sending an e-mail

    Background:
      Given a client authenticated with the API key "client-a"

    Scenario: Client previews a registered template with sample data
      Given they already registered a template named "welcome"
      When they preview the template named "welcome" with sample data
      Then the system returns the rendered subject and body
      And no e-mail is sent

    Scenario: Client tries to preview a template that does not exist
      When they try to preview a template named "missing"
      Then the system shows an error that the template does not exist

  Rule: Clients only see and change their own templates

    Background:
      Given a client authenticated with the API key "client-a"

    Scenario: Client lists their own templates
      Given they already registered a template named "welcome"
      When they list their templates
      Then they see only the template named "welcome"

    Scenario: Client cannot see another client's template
      Given they already registered a template named "welcome"
      And another client authenticated with the API key "client-b" also registered a template named "welcome"
      When they list their templates
      Then they see only their own template named "welcome", not the other client's

    Scenario: Client cannot update another client's template
      Given another client authenticated with the API key "client-b" registered a template named "newsletter"
      When they try to update the template named "newsletter"
      Then the system shows an error that the template does not exist

    Scenario: Client cannot preview another client's template
      Given another client authenticated with the API key "client-b" registered a template named "newsletter"
      When they try to preview the template named "newsletter"
      Then the system shows an error that the template does not exist

    Scenario: Client sees the same templates with a second active API key
      Given they already registered a template named "welcome"
      And they have a second active API key
      When they list their templates with the second API key
      Then they see only the template named "welcome"

  Rule: Every request requires an API key

    Scenario: Request without an API key
      When a request is made without an API key
      Then the system rejects the request as unauthorized

    Scenario: Request with an empty API key
      When a request is made with an empty API key
      Then the system rejects the request as unauthorized

    Scenario: Request with a malformed API key
      When a request is made with a malformed API key
      Then the system rejects the request as unauthorized

    Scenario: Request with an API key that was never issued
      When a request is made with a well-formed API key that was never issued
      Then the system rejects the request as unauthorized

    Scenario: Request with an expired API key
      Given an API key issued for "client-a" that has expired
      When a request is made with that API key
      Then the system rejects the request as unauthorized

    Scenario: Request with a revoked API key
      Given an API key issued for "client-a" that has been revoked
      When a request is made with that API key
      Then the system rejects the request as unauthorized
