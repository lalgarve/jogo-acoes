#!/bin/sh
# Runs once LocalStack is ready (same ready.d init hook as 01-create-queue.sh). LocalStack's
# SES emulation enforces the same "sender must be a verified identity" rule real SES sandbox
# mode does (found in EmailSendHandlerTest, docs/context/iteracao-4.md) -- without this, the
# dev poller's (spec 05-021) first processed message fails with MessageRejectedException.
# Address comes from EMAIL_SENDER_ADDRESS (spec 05-026): since spec 05-031 it is the sender
# address of email-service's test client (jogo-acoes), which scripts/test-api-key.sh restore sets
# in email-service -- email-lambda itself no longer has a sender address of its own.
set -e

awslocal ses verify-email-identity --email-address "${EMAIL_SENDER_ADDRESS:?EMAIL_SENDER_ADDRESS not set}"
