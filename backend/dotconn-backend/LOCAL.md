# Local development

Run `./run-local.sh` from this directory. Uses persistent H2 in `data/` (ignored by Git), no MySQL needed. The default application profile still uses MySQL. Never use the local profile in production.

Optional: export GROQ_API_KEY in the same terminal before starting. JWT_SECRET can be supplied; otherwise a random temporary key is generated on each start, invalidating previous tokens. Do not commit keys.

Frontend: run `npm run dev` in `frontend`. Vite proxies /api to localhost:8080.

Tests: `./mvnw test` uses a separate in-memory H2 database and test-only signing key.

The signup endpoint currently accepts email and password only; the frontend name field is removed until the backend supports storing names. Bearer tokens stay in memory and expire; reloading requires logging in again.

## MySQL on this Mac

Install: `brew install mysql@8.4`
Start: `brew services start mysql@8.4`
Client: `/opt/homebrew/opt/mysql@8.4/bin/mysql`

Run `./run-mysql.sh` to use the `mysql-local` profile. Local database/user credentials are in `.env.mysql.local`, excluded from Git; do not share this file. `./run-local.sh` remains the H2 option. Stop the current backend before changing profiles (both use port 8080).

Tests keep using isolated H2. MySQL compatibility must also be verified against a running MySQL server. Old H2 data is not automatically transferred merely by changing profiles.

MySQL has been installed and verified locally. Existing H2 user accounts were copied to MySQL without changing the original H2 database. Future changes to either database are independent.

Local admin access from this directory: `/opt/homebrew/opt/mysql@8.4/bin/mysql --defaults-extra-file=data/mysql-admin.cnf`. The generated admin password is kept only in that owner-readable, Git-ignored file.
