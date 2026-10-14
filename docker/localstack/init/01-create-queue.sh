#!/bin/sh
# Runs once LocalStack is ready, before the app or any test connects (LocalStack's
# ready.d init hook). Creates the send queue email-service publishes to (email.queue-name) and,
# spec 05-046, its dead-letter queue (email.dlq-name): email-lambda puts there what SES did not
# deliver, with the error type, and the send queue's redrive policy moves there, with no error
# type, a message that failed maxReceiveCount deliveries. email-service consumes the DLQ and
# decides whether to try again. Names kept in sync by hand with EMAIL_QUEUE_NAME's and
# EMAIL_DLQ_NAME's defaults in email-service/src/main/resources/application.yml.
set -e

DLQ_URL=$(awslocal sqs create-queue --queue-name jogo-acoes-email-commands-dlq --query 'QueueUrl' --output text)
DLQ_ARN=$(awslocal sqs get-queue-attributes --queue-url "$DLQ_URL" \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

awslocal sqs create-queue --queue-name jogo-acoes-email-commands \
  --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}"
