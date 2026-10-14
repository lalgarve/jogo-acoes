Feature: Import the lists of disposable e-mail domains
  As operations
  I want to register the repositories of disposable e-mail domain lists and run the batch that imports them
  So that the Serviço de E-mail refuses recipients whose address will be gone in a few minutes

  Background:
    Given no disposable domain repository is registered

  Rule: Operations registers the repositories of the lists

    Background:
      Given operations authenticated with a key that has only the permission "disposable-email:admin"

    Scenario: Operations adds a repository
      Given a list file "main.conf" with the lines:
        """
        mailinator.test
        """
      When they add a repository for the list file "main.conf" described as "Main list"
      Then the repository is created
      And the repository "Main list" is listed with no download and no database update yet

    Scenario: Operations adds a repository whose address is already registered
      Given a list file "main.conf" with the lines:
        """
        mailinator.test
        """
      And they added a repository for the list file "main.conf" described as "Main list"
      When they add a repository for the list file "main.conf" described as "Same list again"
      Then the system rejects the repository because its address is already registered

    Scenario Outline: Operations adds a repository with an invalid address or description
      When they add a repository with the address "<url>" described as "<description>"
      Then the system rejects the request as malformed

      Examples:
        | url                               | description |
        | ftp://lists.example/domains.conf  | FTP list    |
        | lists.example/domains.conf        | No scheme   |
        |                                   | No address  |
        | http://lists.example/domains.conf |             |

  Rule: Each route requires its own permission

    Scenario: A client that only sends e-mail cannot manage the lists
      Given a client authenticated with the API key "jogo-acoes"
      When they list the disposable domain repositories
      Then the system refuses because the key lacks the permission "disposable-email:admin"
      When they ask to run the import of every repository
      Then the system refuses because the key lacks the permission "disposable-email:admin"

    Scenario: Listing the repositories without an API key
      When someone lists the disposable domain repositories without an API key
      Then the system rejects the request as unauthorized

    Scenario: Operations cannot send e-mail nor change templates
      Given operations authenticated with a key that has only the permission "disposable-email:admin"
      When they send the template "welcome" to "player@example.com"
      Then the system refuses because the key lacks the permission "email:send"
      When they register a template named "welcome" with subject "Hi" and a valid body
      Then the system refuses because the key lacks the permission "email:send"

  Rule: Operations runs the batch that imports the lists

    Background:
      Given operations authenticated with a key that has only the permission "disposable-email:admin"

    Scenario: Importing a registered repository
      Given a list file "main.conf" with the lines:
        """
        mailinator.test
        tempmail.test
        """
      And they added a repository for the list file "main.conf" described as "Main list"
      When they ask to run the import of every repository
      Then the import is accepted with one execution for "Main list"
      And the import of "Main list" ends "COMPLETED" with 2 read, 0 skipped, 2 added and 0 removed
      And the disposable domains of "Main list" are "mailinator.test, tempmail.test"
      And the repository "Main list" has a last download and a last database update

    Scenario: Importing again a list that did not change
      Given a list file "main.conf" with the lines:
        """
        mailinator.test
        tempmail.test
        """
      And they added a repository for the list file "main.conf" described as "Main list"
      And the import of every repository already ran
      When they ask to run the import of every repository
      Then the import of "Main list" ends "COMPLETED" with 0 read, 0 skipped, 0 added and 0 removed
      And the last download of "Main list" changed but its last database update did not
      And the disposable domains of "Main list" are "mailinator.test, tempmail.test"

    Scenario: Importing a list that gained one domain and lost another
      Given a list file "main.conf" with the lines:
        """
        mailinator.test
        tempmail.test
        """
      And a list file "other.conf" with the lines:
        """
        tempmail.test
        """
      And they added a repository for the list file "main.conf" described as "Main list"
      And they added a repository for the list file "other.conf" described as "Other list"
      And the import of every repository already ran
      And the list file "main.conf" now has the lines:
        """
        mailinator.test
        throwaway.test
        """
      When they ask to run the import of every repository
      Then the import of "Main list" ends "COMPLETED" with 2 read, 0 skipped, 1 added and 1 removed
      And the disposable domains of "Main list" are "mailinator.test, throwaway.test"
      And the disposable domains of "Other list" are "tempmail.test"
      And the domain "tempmail.test" is still a disposable domain

    Scenario: Importing only one repository
      Given a list file "main.conf" with the lines:
        """
        mailinator.test
        """
      And a list file "other.conf" with the lines:
        """
        tempmail.test
        """
      And they added a repository for the list file "main.conf" described as "Main list"
      And they added a repository for the list file "other.conf" described as "Other list"
      When they ask to run the import of the repository "Main list"
      Then the import is accepted with one execution for "Main list"
      And the import of "Main list" ends "COMPLETED" with 1 read, 0 skipped, 1 added and 0 removed
      And the repository "Other list" is listed with no download and no database update yet

    Scenario: Importing a repository that is already being imported
      Given a repository "Slow list" whose server holds the download
      And they asked to run the import of the repository "Slow list"
      When they ask to run the import of the repository "Slow list"
      Then the system rejects the import because one is already running

    Scenario: Importing a repository that does not exist
      When they ask to run the import of the repository with id 999999999
      Then the system shows an error that the repository does not exist

    Scenario: Reading an import that does not exist
      When they read the import with id 999999999
      Then the system shows an error that the import does not exist

    Scenario Outline: A repository that fails changes nothing and does not stop the others
      Given a list file "main.conf" with the lines:
        """
        mailinator.test
        """
      And they added a repository for the list file "main.conf" described as "Main list"
      And a repository "Broken list" that <failure>
      When they ask to run the import of every repository
      Then the import of "Broken list" ends "FAILED" with the reason "<reason>"
      And the repository "Broken list" is listed with no download and no database update yet
      And the import of "Main list" ends "COMPLETED" with 1 read, 0 skipped, 1 added and 0 removed

      Examples:
        | failure                                   | reason                  |
        | points at a list file that does not exist | HTTP status 404         |
        | points at a port where nothing listens    | could not be downloaded |
        | never answers                             | timed out               |
        | serves a list larger than the limit       | larger than the limit   |

    Scenario: An empty list is refused and nothing is removed
      Given a list file "main.conf" with the lines:
        """
        mailinator.test
        """
      And they added a repository for the list file "main.conf" described as "Main list"
      And the import of every repository already ran
      And the list file "main.conf" now has the lines:
        """
        # nothing left

        """
      When they ask to run the import of every repository
      Then the import of "Main list" ends "FAILED" with the reason "list is empty"
      And the disposable domains of "Main list" are "mailinator.test"

    Scenario: Blank lines, comments, case and spaces are normalized; a line that is not a domain is skipped
      Given a list file "main.conf" with the lines:
        """
        # disposable domains

          Mailinator.TEST
        not a domain
        ação.test
        """
      And they added a repository for the list file "main.conf" described as "Main list"
      When they ask to run the import of every repository
      Then the import of "Main list" ends "COMPLETED" with 5 read, 1 skipped, 2 added and 0 removed
      And the disposable domains of "Main list" are "mailinator.test, xn--ao-siap.test"
