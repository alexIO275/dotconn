# Local development

Run `./run-local.sh` from this directory. Uses persistent H2 in `data/` (ignored by Git), no MySQL needed. The default application profile still uses MySQL. Never use the local profile in production.

Optional: export GROQ_API_KEY in the same terminal before starting. JWT_SECRET can be supplied; otherwise a random temporary key is generated on each start, invalidating previous tokens. Do not commit keys.

Frontend: run `npm run dev` in `frontend`. Vite proxies /api to localhost:8080.

Tests: `./mvnw test` uses a separate in-memory H2 database and test-only signing key.

The signup endpoint currently accepts email and password only; the frontend name field is removed until the backend supports storing names. Bearer tokens stay in memory and expire; reloading requires logging in again.
