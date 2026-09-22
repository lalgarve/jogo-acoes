#!/bin/sh
# Runs once LocalStack is ready (same ready.d init hook as 01-create-queue.sh). LocalStack's
# SES emulation enforces the same "sender must be a verified identity" rule real SES sandbox
# mode does (found in EmailSendHandlerTest, docs/context/iteracao-4.md) -- without this, the
# dev poller's (spec 05-021) first processed message fails with MessageRejectedException.
# Address kept in sync by hand with email.sender-address in
# email-lambda/src/main/resources/application.properties, same convention as
# 01-create-queue.sh's queue name.
set -e

awslocal ses verify-email-identity --email-address no-reply@jogo-acoes.example
