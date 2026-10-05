#!/bin/sh
set -eu
cd "$(dirname "$0")"
original_billing_0=${STRIPE_SECRET_KEY:-}
original_billing_1=${STRIPE_WEBHOOK_SECRET:-}
original_billing_2=${STRIPE_PRICE_BRONZE:-}
original_billing_3=${STRIPE_PRICE_SILVER:-}
original_billing_4=${STRIPE_PRICE_GOLD:-}
original_billing_5=${BILLING_FRONTEND_URL:-}
# Local credentials file is ignored by Git. Existing shell variables take precedence.
if [ -f .env.mysql.local ]; then
  original_db_password=${DB_PASSWORD:-}
  original_db_username=${DB_USERNAME:-}
  original_jwt_secret=${JWT_SECRET:-}
  set -a
  . ./.env.mysql.local
  set +a
  if [ -n "$original_db_password" ]; then DB_PASSWORD=$original_db_password; fi
  if [ -n "$original_db_username" ]; then DB_USERNAME=$original_db_username; fi
  if [ -n "$original_jwt_secret" ]; then JWT_SECRET=$original_jwt_secret; fi
  export DB_PASSWORD DB_USERNAME JWT_SECRET
fi
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
: "${DB_PASSWORD:?Configurează DB_PASSWORD în mediu sau în .env.mysql.local}"
if [ -z "${JWT_SECRET:-}" ]; then
  JWT_SECRET=$(openssl rand -hex 32)
  export JWT_SECRET
fi
if [ -z "${GROQ_API_KEY:-}" ]; then
  echo "Groq nu este configurat. Setează GROQ_API_KEY înainte de pornire pentru analiză."
fi
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql-local
