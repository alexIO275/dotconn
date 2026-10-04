# MicroCrew frontend

Vite + TypeScript.

```sh
npm install
npm run dev
```

Production build: `npm run build`.

Authentication integration points: `#login-button` and `#register-button` in `src/main.ts`.

Routes: `/`, `/login`, `/register`. Production hosting must serve `index.html` for unknown paths (SPA fallback).

Login and Register include browser validation, password visibility, and password confirmation. Connect the submit handler in `src/main.ts` to the backend; credentials are currently neither transmitted nor stored.
