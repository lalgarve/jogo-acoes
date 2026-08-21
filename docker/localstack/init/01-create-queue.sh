#!/bin/sh
# Runs once LocalStack is ready, before the app or any test connects (LocalStack's
# ready.d init hook). Creates the queue application-docker.yml's email.queue-name points at
# -- SqsEmailSender publishes here, and it's the only queue this iteration needs
# (docs/context/iteracao-4.md, "Produtor: EmailSender real"). Name kept in sync by hand with
# EMAIL_QUEUE_NAME's default in app/src/main/resources/application.yml.
set -e

awslocal sqs create-queue --queue-name jogo-acoes-email-commands
