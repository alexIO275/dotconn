#!/bin/sh
set -eu
cd "$(dirname "$0")"
original_billing_0=${STRIPE_SECRET_KEY:-}
original_billing_1=${STRIPE_WEBHOOK_SECRET:-}
original_billing_2=${STRIPE_PRICE_BRONZE:-}
original_billing_3=${STRIPE_PRICE_SILVER:-}
original_billing_4=${STRIPE_PRICE_GOLD:-}
original_billing_5=${BILLING_FRONTEND_URL:-}
# Opt-in Stripe TEST configuration; this file is ignored and must not contain live keys.
if [ -f .env.stripe.local ]; then
  set -a
  . ./.env.stripe.local
  set +a
  if [ -n "$original_billing_0" ]; then STRIPE_SECRET_KEY=$original_billing_0; fi
  if [ -n "$original_billing_1" ]; then STRIPE_WEBHOOK_SECRET=$original_billing_1; fi
  if [ -n "$original_billing_2" ]; then STRIPE_PRICE_BRONZE=$original_billing_2; fi
  if [ -n "$original_billing_3" ]; then STRIPE_PRICE_SILVER=$original_billing_3; fi
  if [ -n "$original_billing_4" ]; then STRIPE_PRICE_GOLD=$original_billing_4; fi
  if [ -n "$original_billing_5" ]; then BILLING_FRONTEND_URL=$original_billing_5; fi
  export STRIPE_SECRET_KEY STRIPE_WEBHOOK_SECRET STRIPE_PRICE_BRONZE STRIPE_PRICE_SILVER STRIPE_PRICE_GOLD BILLING_FRONTEND_URL
fi
# Generate a temporary signing key if one is not already supplied.
# Tokens from previous runs expire when this generated key changes.
if [ -z "${JWT_SECRET:-}" ]; then
  JWT_SECRET=$(openssl rand -hex 32)
  export JWT_SECRET
fi
if [ -z "${GROQ_API_KEY:-}" ]; then
  echo "Groq nu este configurat. Login/Register funcționează; analiza va returna 503."
fi
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
