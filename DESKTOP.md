# MicroCrew desktop — Electron

Aplicatia desktop foloseste acelasi frontend Vite + TypeScript si acelasi API Spring ca versiunea web. Backendul si baza de date raman servicii separate. Nu sunt incluse Java, MySQL, cheile Groq sau Stripe in aplicatia distribuita.

## Pornire pentru dezvoltare

Cerinte: Node 22.12 sau mai nou, npm si backendul Spring pornit. Electron descarca propriul runtime; nu este nevoie de o instalare Electron globala.

Terminal 1, backend cu MySQL si datele demo:

```sh
cd backend/dotconn-backend
MICROCREW_DEMO_ENABLED=true ./run-mysql.sh
```

Alternativ, foloseste `MICROCREW_DEMO_ENABLED=true ./run-local.sh` pentru H2. Cheile si conexiunea DB sunt configurate in backend, conform README si BILLING.md.

Terminal 2, din radacina repository-ului:

```sh
cd frontend
npm install
npm run dev:desktop
```

Comanda compileaza procesul Electron, porneste Vite pe `127.0.0.1:5174` si deschide fereastra MicroCrew. Frontendul are hot reload. Dupa modificari in `electron/`, reporneste comanda. Portul 5173 ramane disponibil pentru dezvoltarea versiunii web.

Pentru interfata construita, fara Vite:

```sh
cd frontend
npm run start:desktop
```

## Aplicatie si instalatoare

```sh
cd frontend
npm run package:desktop
```

Pe acest Mac Apple Silicon, rezultatul este `frontend/release/mac-arm64/MicroCrew.app`. Se poate deschide din Finder. Aplicatia include interfata construita si un server HTTP intern legat exclusiv la `127.0.0.1`, cu port alocat automat. Rutele SPA, REST `/api` si WebSocket `/ws` functioneaza fara un proces Vite separat.

```sh
npm run dist:desktop
```

Genereaza distributia pentru sistemul pe care ruleaza buildul: DMG si ZIP pe macOS, NSIS pe Windows, AppImage pe Linux. Buildul macOS local foloseste semnare ad-hoc. Distribuirea publica pe macOS necesita configurarea separata a certificatului Developer ID si a notarizarii; versiunea locala nu este notarizata. Buildurile Windows/Linux nu au fost executate pe acest Mac.

Fisierele generate (`dist`, `dist-electron`, `release`) sunt ignorate de Git. Installerul contine frontendul si componentele Electron, nu folderul backend sau fisierele `.env`.

## Configurare

| Variabila | Implicit | Scop |
| --- | --- | --- |
| `MICROCREW_BACKEND_URL` | `http://127.0.0.1:8080` | Originea API-ului Spring; HTTPS pentru un server remote |
| `MICROCREW_BILLING_RETURN_ORIGIN` | `http://127.0.0.1:5173` | Trebuie sa corespunda `BILLING_FRONTEND_URL` din backend, daca acesta a fost schimbat |

Exemplu cu backend remote:

```sh
MICROCREW_BACKEND_URL='https://api.example.com' \
MICROCREW_BILLING_RETURN_ORIGIN='https://app.example.com' \
npm run start:desktop
```

Adresa backendului se configureaza in mediul procesului desktop, nu in `VITE_*`. Nu pune chei API in frontend. Pentru aplicatia macOS deja impachetata, aceleasi variabile pot fi furnizate la pornirea executabilului `MicroCrew.app/Contents/MacOS/MicroCrew`.

## Stripe si linkuri externe

Checkout si Customer Portal se deschid intr-o fereastra Electron separata, fara preload, acces Node sau acces la JWT-ul aplicatiei. Revenirea catre `/billing` este interceptata si trimisa ferestrei MicroCrew, care isi pastreaza sesiunea si verifica abonamentul prin backend. Inchiderea checkout-ului revine la planul confirmat. Nu este necesar un server web pe portul 5173 pentru aceasta revenire in aplicatia desktop.

Configuratia locala existenta `BILLING_FRONTEND_URL=http://127.0.0.1:5173` ramane valabila pentru ambele versiuni. Stripe ramane in modul TEST. Webhookul si Stripe CLI continua sa ruleze langa backend, conform BILLING.md.

Linkurile HTTPS catre repository/profil GitHub se deschid in browserul sistemului. Aplicatia desktop nu incarca acele pagini in fereastra cu preload.

## Verificare si limite

```sh
cd frontend
npm run test:desktop
npm run build:desktop
```

Testele verifica rutele SPA si asseturile impachetate, proxy-ul REST cu JWT si payload, transportul WebSocket, backendul indisponibil, validarea linkurilor si a revenirii din Stripe, plus respingerea originilor straine si a traversarii de directoare.

Sesiunea ramane in `sessionStorage`, ca in versiunea web: refresh-ul o pastreaza, inchiderea ferestrei necesita o noua autentificare. Aplicatia desktop nu adauga un editor comun, acces la fisierele utilizatorului, sincronizare GitHub sau mod offline. Acestea necesita implementari separate.

## Surse

- [Securitate si izolare in Electron](https://www.electronjs.org/docs/latest/tutorial/security)
- [BrowserWindow](https://www.electronjs.org/docs/latest/api/browser-window)
- [Distribuirea cu electron-builder](https://www.electron.build/configuration.html)
