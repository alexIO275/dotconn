# MicroCrew: demo local

Datele sunt fictive și folosesc domeniul rezervat `.test`. Seed-ul este dezactivat implicit și funcționează numai cu profilul backend `local` sau `mysql-local`.

## Pornire

Frontend:

```sh
cd /Users/banurobert/Desktop/dotconn/frontend
npm run dev -- --host 127.0.0.1
```

Backend MySQL, folosind configurația locală existentă:

```sh
cd /Users/banurobert/Desktop/dotconn/backend/dotconn-backend
MICROCREW_DEMO_ENABLED=true ./run-mysql.sh
```

Alternativ, cu H2 persistent local:

```sh
cd /Users/banurobert/Desktop/dotconn/backend/dotconn-backend
MICROCREW_DEMO_ENABLED=true ./run-local.sh
```

Profilul `demo` poate fi activat și explicit împreună cu `local` sau `mysql-local`, de exemplu `-Dspring-boot.run.profiles=mysql-local,demo`; credențialele bazei de date trebuie furnizate în mediu. Pentru pornirile obișnuite nu trebuie activat nici profilul, nici variabila demo.

Deschide <http://127.0.0.1:5173/>. Toate conturile de mai jos au parola publică **`DemoCrew2026!`**. Folosește o fereastră separată/incognito sau alt browser pentru a demonstra clientul și programatorii în paralel.

## Conturi

| Cont | Rol | Disponibilitate | Tarif/oră |
| --- | --- | --- | --- |
| `client@demo.microcrew.test` | Client, trei proiecte | — | — |
| `studio@demo.microcrew.test` | Client, proiect fără echipă eligibilă | — | — |
| `ana.frontend@demo.microcrew.test` | Frontend: TypeScript, React, Vite, CSS | Disponibilă | 28 |
| `radu.backend@demo.microcrew.test` | Backend: Java, Spring, MySQL, REST | Disponibil | 32 |
| `ioana.frontend@demo.microcrew.test` | Frontend: TypeScript, React, Next.js | Parțial disponibilă | 24 |
| `andrei.backend@demo.microcrew.test` | Backend: Node.js, TypeScript, PostgreSQL, REST | Disponibil | 27 |
| `alex.fullstack@demo.microcrew.test` | Full-stack: React, TypeScript, Java, Spring, MySQL | Disponibil | 38 |
| `mara.mobile@demo.microcrew.test` | Mobile: React Native, TypeScript, Expo | Disponibilă | 30 |
| `vlad.devops@demo.microcrew.test` | DevOps: Docker, AWS, GitHub Actions, Linux | Parțial disponibil | 35 |
| `elena.qa@demo.microcrew.test` | QA: Playwright, JUnit, Postman, TypeScript | Disponibilă | 22 |
| `mihai.data@demo.microcrew.test` | Data: Python, SQL, Pandas, PostgreSQL | Disponibil | 34 |
| `daria.security@demo.microcrew.test` | Security: OWASP, Java, Spring, OAuth2 | Indisponibilă | 45 |

Tarifele sunt valori demonstrative în aceeași unitate cu plafonul proiectului; backendul nu definește o monedă.

## Scenarii pregătite

1. **Shop local — găsește echipa** (`client`): frontend + backend, plafon 40/oră. Nu are invitații; folosește propunerile automate pentru a invita un pachet complet.
2. **Booking Hub — invitații în așteptare** (`client`): Ana și Radu au invitații pending, Alex are o invitație declined. Intră ca Ana/Radu pentru acceptare sau refuz, apoi revino ca client.
3. **LaunchBoard — workspace activ** (`client`): Ana și Radu sunt membri acceptați. Are repository, contract API și patru sarcini în stări todo/in-progress/done, inclusiv o sarcină neatribuită. Conturile au conversații private demo client–dev și dev–dev, plus trei mesaje în canalul comun al proiectului.
4. **SecurePay — buget insuficient** (`studio`): cere backend + security cu plafon 15/oră. Nu există o echipă eligibilă; verifică mesajul despre rolurile neacoperite. Poți edita plafonul sau cerințele pentru a vedea schimbarea rezultatului.

Pentru fluxul complet: client → Shop local → propune și invită echipa → conturile invitate acceptă → client verifică membrii și creează sarcini → membrii actualizează sarcinile și comunică prin chat.

Proiectele sunt deja create manual. **Groq nu este necesar pentru aceste scenarii.** Analiza unei descrieri noi cu AI necesită separat `GROQ_API_KEY` în mediul backendului; cheia nu se pune în frontend și nu se salvează în Git.

## Demonstrarea abonamentelor

Pagina `/pricing` prezintă Free și cele trei produse Stripe existente: Bronze 9,99 USD, Silver 19,99 USD și Gold 34,99 USD pe lună. Limitele de analize AI reușite sunt 3 / 25 / 50 / 200 pe lună calendaristică UTC și se aplică în backend. Proiectele, echipele și chatul sunt accesibile și fără abonament plătit.

Fluxul Stripe rulează exclusiv în modul de test: checkout găzduit de Stripe → confirmare pe server → plan salvat în cont → administrare în Customer Portal. Cardurile și tranzacțiile de test nu transferă bani reali. Configurarea și scenariul complet sunt în [BILLING.md](BILLING.md).

## Persistență

Seed-ul creează datele într-o singură tranzacție și salvează markerul `microcrew-demo-v1` în tabelul `demo_seed_runs`. La restart nu dublează conturi, proiecte, invitații, mesaje sau sarcini și nu resetează modificările făcute în demo. Dezactivarea opțiunii oprește seed-ul; datele create rămân în baza locală.

Dacă primul seed găsește deja un cont cu una dintre adresele rezervate, se oprește fără a modifica datele existente. Nu șterge markerul singur și nu folosi resetarea întregii baze de date pe baza de lucru a colegilor. Pentru un demo complet nou, folosește o bază separată și configurează `DB_URL` înainte de pornire.
