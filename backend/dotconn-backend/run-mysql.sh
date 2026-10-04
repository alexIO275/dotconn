#!/bin/sh
set -eu
cd "$(dirname "$0")"
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
: "${DB_PASSWORD:?Configurează DB_PASSWORD în mediu sau în .env.mysql.local}"
if [ -z "${JWT_SECRET:-}" ]; then
  JWT_SECRET=$(openssl rand -hex 32)
  export JWT_SECRET
fi
if [ -z "${GROQ_API_KEY:-}" ]; then
  echo "Groq nu este configurat. Setează GROQ_API_KEY înainte de pornire pentru analiză."
fi
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql-local
