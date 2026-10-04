# Project analysis — Groq

POST /api/project-analysis
Body: {"description":"Am frontendul în React și caut ajutor pentru API și autentificare."}
Response: {"summary":"...","roles":["backend"],"tasks":["..."],"existingStack":["React"],"missingInformation":["Care este bugetul?"]}

Set GROQ_API_KEY in the server process environment. Optional GROQ_MODEL defaults to openai/gpt-oss-20b (strict structured outputs). Never put the key in VITE_* or commit it. Spring does not automatically load a .env file. No key returns HTTP 503. No live provider call has been made during development.

Errors: 400 input invalid; 429 upstream rate limit; 502 upstream failure/invalid output; 503 missing configuration; 504 timeout. Provider response bodies and keys are not returned or logged.

Backend integration: retain your existing security policy. This controller does not override Spring Security. Coordinate authentication/CSRF with the auth team before testing through the frontend; an authenticated session or bearer token may be required. Add appropriate per-user rate limiting before public release.

Frontend uses /api/project-analysis via Vite dev proxy to http://127.0.0.1:8080 and sends a Bearer token obtained by login/signup. Production must reverse-proxy /api to Spring. Local development: see LOCAL.md and run-local.sh.

Docs: https://console.groq.com/docs/structured-outputs and https://console.groq.com/docs/openai
