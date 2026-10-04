#!/bin/sh
set -eu
cd "$(dirname "$0")"
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
