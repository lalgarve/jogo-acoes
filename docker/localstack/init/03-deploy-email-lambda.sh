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
# Spec 05-046: the function also publishes to the DLQ (01-create-queue.sh) -- its SQS client gets
# the same LocalStack endpoint as SES, and EMAIL_DLQ_NAME names the queue.
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
    QUARKUS_SES_AWS_CREDENTIALS_STATIC_PROVIDER_SECRET_ACCESS_KEY=test,
    QUARKUS_SQS_ENDPOINT_OVERRIDE=http://localhost.localstack.cloud:4566,
    QUARKUS_SQS_AWS_REGION=us-east-1,
    QUARKUS_SQS_AWS_CREDENTIALS_TYPE=static,
    QUARKUS_SQS_AWS_CREDENTIALS_STATIC_PROVIDER_ACCESS_KEY_ID=test,
    QUARKUS_SQS_AWS_CREDENTIALS_STATIC_PROVIDER_SECRET_ACCESS_KEY=test,
    EMAIL_DLQ_NAME=jogo-acoes-email-commands-dlq
  }'

# Spec 05-055: the waits below fail the script when what they wait for is not ready in time --
# that marks it ERROR in /_localstack/init/ready, which is what localstack's healthcheck in
# docker-compose.yml reads; going on would leave a function that never runs behind a "healthy"
# container. Timeouts in seconds, not attempts: each awslocal call takes up to ~1.5s, so 30
# attempts once took ~52s in WSL. Both are only an upper limit -- each wait ends as soon as the
# state is reached (the function is usually Active in a few seconds; its first start pulls the
# Lambda runtime image, which took longer than 50s once). The healthcheck's start_period covers
# both plus the rest of the init.
FUNCTION_TIMEOUT_SECONDS=120
MAPPING_TIMEOUT_SECONDS=30

# wait_for_state WHAT EXPECTED TIMEOUT COMMAND...: runs COMMAND every second until it prints
# EXPECTED. Returns 1, saying what was waited for and the last state, after TIMEOUT seconds or as
# soon as the state is Failed, which a function never leaves on its own.
wait_for_state() {
  what="$1"
  expected="$2"
  timeout="$3"
  shift 3
  start=$(date +%s)
  state=""
  while [ $(( $(date +%s) - start )) -lt "$timeout" ]; do
    state=$("$@")
    if [ "$state" = "$expected" ]; then
      return 0
    fi
    if [ "$state" = Failed ]; then
      echo "03-deploy-email-lambda: $what is Failed instead of $expected" >&2
      return 1
    fi
    sleep 1
  done
  echo "03-deploy-email-lambda: $what did not become $expected in ${timeout}s, last state: ${state:-none}" >&2
  return 1
}

# The function stays "Pending" while LocalStack builds its execution container (pulling the Lambda
# runtime image the first time) -- an event source mapping can't attach to a function that isn't
# Active yet. On failure, LocalStack's reason (e.g. the runtime image it could not pull) goes to
# the log too.
if ! wait_for_state EmailLambda Active "$FUNCTION_TIMEOUT_SECONDS" \
  awslocal lambda get-function --function-name EmailLambda --query 'Configuration.State' --output text; then
  awslocal lambda get-function --function-name EmailLambda \
    --query 'Configuration.[StateReasonCode,StateReason]' --output text >&2 || true
  exit 1
fi

QUEUE_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url "http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/${QUEUE_NAME}" \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

MAPPING_UUID=$(awslocal lambda create-event-source-mapping \
  --function-name EmailLambda \
  --event-source-arn "$QUEUE_ARN" \
  --batch-size 1 \
  --query 'UUID' --output text)

# The mapping starts as "Creating"; only "Enabled" means the queue's messages reach the function.
wait_for_state "the event source mapping from ${QUEUE_NAME}" Enabled "$MAPPING_TIMEOUT_SECONDS" \
  awslocal lambda get-event-source-mapping --uuid "$MAPPING_UUID" --query 'State' --output text ||
  exit 1
