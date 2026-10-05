# Abonamente MicroCrew — Stripe test

Integrarea foloseste Stripe Checkout pentru abonamente recurente si Customer Portal pentru administrare. Aplicatia este configurata exclusiv pentru chei si evenimente de test. Nu se debiteaza bani reali in acest flux.

## Planuri

| Plan | Pret lunar | Analize AI reusite / luna calendaristica UTC |
| --- | --- | --- |
| Free | 0 USD | 3 |
| Bronze | 9.99 USD | 25 |
| Silver | 19.99 USD | 50 |
| Gold | 34.99 USD | 200 |

Profilurile, proiectele, propunerile de echipe, invitatiile, workspace-ul si chatul sunt disponibile si in planul gratuit. Diferenta implementata intre planuri este limita de analize AI. Gold nu reprezinta o certificare a competentelor, iar plata nu modifica rankingul programatorilor.

Preturile sunt cele ale produselor Stripe existente ale proiectului. Modelul economic trebuie validat cu utilizatori reali; tranzactiile de test nu reprezinta venituri.

## Configurare locala

Din `backend/dotconn-backend`, copiaza `.env.stripe.local.example` in `.env.stripe.local` daca fisierul local nu exista deja. Fisierul local este ignorat de Git; nu trimite cheile in chat si nu le pune in `VITE_*`.

```sh
STRIPE_SECRET_KEY='sk_test_...'
STRIPE_WEBHOOK_SECRET='whsec_...'
STRIPE_PRICE_BRONZE='price_...'
STRIPE_PRICE_SILVER='price_...'
STRIPE_PRICE_GOLD='price_...'
BILLING_FRONTEND_URL='http://127.0.0.1:5173'
```

Price IDs se gasesc in Stripe Dashboard, in mediul test/sandbox, in pagina fiecarui produs, la pretul recurent. Sunt ID-uri `price_...`, nu ID-uri `prod_...`. Preturile trebuie sa fie active, lunare, in USD si cu valorile 999, 1999, respectiv 3499 centi. Backendul verifica aceste proprietati inainte de checkout.

Nu trebuie create alte produse Stripe. Pagina de abonamente functioneaza si fara chei, dar checkout-ul ramane indisponibil pana la completarea configuratiei.

### Webhook pentru localhost

Stripe CLI trebuie instalat si conectat la acelasi cont/sandbox ca cheia de test. Alternativ, foloseste cheia din fisier prin variabila de mediu a CLI-ului:

```sh
cd backend/dotconn-backend
set -a
. ./.env.stripe.local
set +a
export STRIPE_API_KEY="$STRIPE_SECRET_KEY"
stripe listen \
  --events checkout.session.completed,checkout.session.async_payment_succeeded,customer.subscription.created,customer.subscription.updated,customer.subscription.deleted,invoice.paid,invoice.payment_failed \
  --forward-to http://127.0.0.1:8080/api/billing/webhook
```

CLI-ul afiseaza secretul de semnare `whsec_...`. Pune-l in `STRIPE_WEBHOOK_SECRET` din fisierul local si lasa procesul `stripe listen` pornit in timpul demonstratiei. Secretul CLI-ului este diferit de cel al unui webhook configurat separat in Dashboard.

Porneste sau reporneste backendul pentru incarcarea configuratiei:

```sh
cd backend/dotconn-backend
MICROCREW_DEMO_ENABLED=true ./run-mysql.sh
```

`run-mysql.sh` si `run-local.sh` incarca fisierul Stripe local. La rularea directa cu Maven sau JAR, variabilele trebuie furnizate in mediul procesului.

Pentru Customer Portal, activeaza o configuratie de test in Stripe Dashboard. Portalul foloseste optiunile configurate in contul Stripe. Configuratia verificata pentru demo permite anularea; schimbarea intre Bronze, Silver si Gold din portal necesita activarea separata a optiunii de actualizare a abonamentului si selectarea celor trei preturi.

## Scenariu de demonstratie

1. Deschide `http://127.0.0.1:5173/pricing` si prezinta cele trei abonamente si accesul gratuit de baza.
2. Autentifica-te si deschide `/billing` pentru planul curent si utilizarea AI.
3. Alege Bronze, Silver sau Gold. Backendul creeaza sesiunea pe pretul configurat, iar browserul deschide checkout-ul gazduit de Stripe.
4. In checkout foloseste cardul de test `4242 4242 4242 4242`, o data viitoare si orice CVC de trei cifre. Nu folosi date de card real.
5. Dupa checkout revii la `/billing`. Backendul verifica sesiunea si abonamentul Stripe, iar contul afiseaza planul confirmat si noua limita AI.
6. Din administrarea abonamentului poti demonstra anularea in Customer Portal. Modificarile sunt sincronizate prin webhook.

Redirectul de succes nu activeaza singur planul: starea este verificata pe server. Webhook-urile au semnatura verificata si sunt procesate fara aplicarea repetata a aceluiasi eveniment.

Daca revii dintr-un checkout neplatit, alegerea aceluiasi plan reutilizeaza sesiunea deschisa. Alegerea altui plan expira mai intai sesiunea veche si creeaza una noua. Daca plata s-a finalizat intre timp, backendul sincronizeaza abonamentul si blocheaza crearea unui al doilea checkout.

Analizele care esueaza nu consuma cota. Limita se aplica in backend si se reseteaza la inceputul lunii calendaristice UTC, independent de ziua in care se reinnoieste abonamentul Stripe. Pentru analiza live este necesara separat cheia Groq.

## Verificare locala

Checkout-ul Bronze a fost parcurs in browser cu un card Stripe de test: contul demo a trecut de la Free (3 analize) la Bronze activ (25). Evenimentele `invoice.paid`, `checkout.session.completed` si `customer.subscription.created` au primit HTTP 200 de la webhook-ul local. Portalul a confirmat anularea programata, iar accesul Bronze ramane pana la sfarsitul perioadei; backendul interpreteaza si `cancel_at` pentru abonamentele Stripe cu flexible billing.

Planul a ramas salvat dupa repornirea backendului. Un al doilea cont a ramas Free, iar o analiza nereusita fara cheia Groq nu i-a consumat cota. Pagina a fost verificata si la latimea de 390 px, fara scroll orizontal.

Schimbarea checkout-ului neplatit Bronze in Silver a fost verificata in Stripe Sandbox: sesiunea Bronze devine `expired`, Silver ramane `open` la 1999 centi USD, iar contul ramane Free. Suita backend are 68 de teste trecute, inclusiv semnaturi, replay, acces intre conturi, cote concurente si plata finalizata in timpul schimbarii planului; buildul frontend a trecut.

## API

- `GET /api/billing/plans` — catalog public si starea configuratiei.
- `GET /api/billing/subscription` — planul si utilizarea contului autentificat.
- `POST /api/billing/checkout` — de exemplu `{ "plan": "silver" }`; valori acceptate: `bronze`, `silver`, `gold`.
- `POST /api/billing/sync` — `{ "sessionId": "cs_test_..." }`, verificare server dupa intoarcerea din checkout.
- `POST /api/billing/portal` — sesiune Customer Portal pentru contul autentificat.
- `POST /api/billing/webhook` — eveniment Stripe semnat; fara token JWT, cu verificarea semnaturii pe corpul original al cererii.

## Surse

- [Stripe Checkout pentru abonamente](https://docs.stripe.com/payments/checkout/build-subscriptions)
- [Evenimentele abonamentelor](https://docs.stripe.com/billing/subscriptions/webhooks)
- [Verificarea semnaturii webhook](https://docs.stripe.com/webhooks/signature)
- [Testarea platilor](https://docs.stripe.com/testing)
- [Customer Portal](https://docs.stripe.com/customer-management)
