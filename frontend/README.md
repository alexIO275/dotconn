# MicroCrew — web si desktop

Frontendul este o SPA TypeScript + Vite, cu Electron pentru versiunea desktop. Necesita Node 22.12+ si backendul Spring pornit pe `127.0.0.1:8080`, sau `MICROCREW_BACKEND_URL` configurat.

```sh
npm install
npm run dev           # web, port 5173
npm run dev:desktop   # Electron + Vite, port 5174
npm run start:desktop # Electron cu frontendul construit, fara Vite
```

```sh
npm run build
npm run test:desktop
npm run package:desktop # aplicatie locala in release/
npm run dist:desktop    # distributie pentru platforma curenta
```

Login/Register folosesc API-ul Spring; JWT-ul ramane in `sessionStorage`. Proiectele, matching-ul, invitatiile, workspace-ul, chatul si abonamentele folosesc aceleasi endpoint-uri in ambele versiuni.

In Electron, procesul principal serveste asseturile construite, proxiaza REST/WebSocket si deschide Stripe intr-o fereastra izolata. Rendererul nu are acces Node, iar preload-ul expune numai operatiile pentru Stripe, linkuri HTTPS si revenirea la abonament.

Configurarea, distribuirea si limitele sunt descrise in [DESKTOP.md](../DESKTOP.md). Cheile Stripe/Groq raman exclusiv in backend, conform [BILLING.md](../BILLING.md).
