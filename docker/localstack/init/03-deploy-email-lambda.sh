#!/bin/sh
# Runs once LocalStack is ready, after 01-create-queue.sh (the queue must exist before this
# script can look up its ARN) and 02-verify-ses-sender.sh -- ready.d scripts run in filename
# order, same convention already used by those two.
#
# Deploys email-lambda's real packaged artifact (function.zip, built by the email-lambda-builder
# service before this container even starts -- see docker-compose.yml) as an actual Lambda
# function, then wires a real SQS event source mapping from the command queue to it. This is
# the same trigger mechanism real AWS would use in production (docs/context/iteracao-4.md,
# decision 1) -- LocalStack genuinely supports it (Issue #87, full investigation in
# specs/05-022-harness-teste-consumo-email/investigacao-issue-87.md), so no custom consumer
# code (the old EmailQueuePoller, removed) is needed to bridge the gap locally.
#
# No sender address for the function (spec 05-031): each message carries its client's own.
set -e

FUNCTION_ZIP=/tmp/email-lambda-target/function.zip
QUEUE_NAME=jogo-acoes-email-commands

awslocal lambda create-function \
  --function-name EmailLambda \
  --zip-file "fileb://${FUNCTION_ZIP}" \
  --handler "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest" \
  --runtime java21 \
  --role arn:aws:iam::000000000000:role/lambda-role \
  --timeout 15 \
  --memory-size 256 \
  --environment 'Variables={
    QUARKUS_SES_ENDPOINT_OVERRIDE=http://localhost.localstack.cloud:4566,
    QUARKUS_SES_AWS_REGION=us-east-1,
    QUARKUS_SES_AWS_CREDENTIALS_TYPE=static,
    QUARKUS_SES_AWS_CREDENTIALS_STATIC_PROVIDER_ACCESS_KEY_ID=test,
    QUARKUS_SES_AWS_CREDENTIALS_STATIC_PROVIDER_SECRET_ACCESS_KEY=test
  }'

# The function stays "Pending" for a few seconds while LocalStack builds its execution
# container -- an event source mapping can't attach to a function that isn't Active yet.
for i in $(seq 1 30); do
  STATE=$(awslocal lambda get-function --function-name EmailLambda --query 'Configuration.State' --output text)
  if [ "$STATE" = "Active" ]; then
    break
  fi
  sleep 1
done

QUEUE_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url "http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/${QUEUE_NAME}" \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

awslocal lambda create-event-source-mapping \
  --function-name EmailLambda \
  --event-source-arn "$QUEUE_ARN" \
  --batch-size 1
