# AdriFitt Backend

API REST de la plataforma de entrenamiento personal: clientes, planes y suscripciones, rutinas,
dietas, seguimientos, fotos de progreso, registro de entrenos, analíticas, mensajería, cobros,
emails, revisiones y tareas, reseñas e información de negocio.

## Stack

- Java 21 · Spring Boot 3.5 · Spring Security + JWT · Spring Data JPA
- PostgreSQL (producción) · H2 (tests y perfil `local`)
- Flyway (migraciones versionadas) · OpenPDF · Spring Mail
- Maven (wrapper incluido)

## Arquitectura

Monolito modular organizado por *features*. Cada módulo tiene `controller → service → repository → domain`,
con `dto`/`mapper` para no exponer entidades. Los módulos se comunican con eventos de Spring cuando
no deben conocerse entre sí (p. ej. una suscripción publica `SubscriptionChargeEvent` y el módulo de
pagos crea el cobro; borrar un cliente publica `ClientDeletedEvent` y cada módulo borra sus datos).

```
com.adrifit.backend
├── auth           login, /me, cambio de contraseña
├── user           usuarios y roles (TRAINER / CLIENT)
├── client         clientes, alta con contraseña temporal + email de bienvenida
├── plan           planes (precio mensual, frecuencia de revisión, funcionalidades)
├── subscription   suscripciones mensuales, cambio/cancelación por el cliente, renovaciones
├── payment        cobros: tarjeta y Bizum (pasarela en modo test) y efectivo (solo entrenador)
├── email          bandeja de salida + envío (modo test o SMTP) y plantillas
├── message        chat cliente ⇄ entrenador (según plan)
├── workout        rutinas y asignación
├── workoutlog     registro de entrenos: carga y RIR por serie, progreso por ejercicio
├── diet           dietas y asignación
├── report         seguimientos (peso, medidas, sensaciones) y feedback del entrenador
├── photo          fotos de progreso
├── analysis       analíticas médicas (PDF)
├── task           tareas del entrenador y calendario de revisiones
├── testimonial    reseñas de clientes (una por cliente) para la landing
├── dashboard      paneles de entrenador y cliente, información de negocio
├── jobs           tareas diarias (renovaciones, recordatorios de revisión)
└── common         seguridad, configuración, excepciones, almacenamiento, eventos
```

## Base de datos y migraciones

El esquema lo gestiona **Flyway** (`src/main/resources/db/migration`) y Hibernate solo lo **valida**
(`ddl-auto: validate`): si una entidad no coincide con la base de datos la aplicación no arranca.

| Migración | Contenido |
|---|---|
| `V1__initial_schema.sql` | Esquema original. En bases de datos creadas antes de Flyway se marca como *baseline*. |
| `V2__schema_review_and_new_modules.sql` | Corrige la deriva del antiguo `ddl-auto=update` (UNIQUE obsoleto en `subscriptions.client_id`, columna huérfana `clients.status`), añade FKs e índices que faltaban y crea las tablas nuevas. |
| `V3__one_current_subscription_per_client` (Java) | Índice único parcial en PostgreSQL: solo una suscripción *actual* por cliente. |

Una base de datos existente se actualiza sola al arrancar (`baseline-on-migrate`).

> **Nota:** en la base de datos local las tablas `diets`, `diet_days`, `diet_meals`, `diet_foods`,
> `diet_alternatives` y `client_diets` pertenecen al usuario `postgres` (se crearon a mano). La app
> puede leer y escribir en ellas, pero no alterarlas en futuras migraciones. Para corregirlo, ejecuta
> como `postgres`:
>
> ```sql
> ALTER TABLE diets OWNER TO adrifit;       ALTER TABLE diet_days OWNER TO adrifit;
> ALTER TABLE diet_meals OWNER TO adrifit;  ALTER TABLE diet_foods OWNER TO adrifit;
> ALTER TABLE diet_alternatives OWNER TO adrifit; ALTER TABLE client_diets OWNER TO adrifit;
> ```

## Ejecutar

Requiere JDK 21. No hace falta instalar Maven. La forma más sencilla (localiza el JDK aunque no
tengas `JAVA_HOME`):

```powershell
.\run.ps1          # con PostgreSQL
.\run.ps1 -Local   # sin PostgreSQL: H2 + datos de demo
```

O con el wrapper directamente (necesita `JAVA_HOME`):

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.11"

# Con PostgreSQL (base de datos `adrifit`, usuario/contraseña `adrifit`)
.\mvnw.cmd spring-boot:run

# Sin PostgreSQL: H2 en fichero (./data) + datos de demo
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Usuarios:

| Usuario | Contraseña | Notas |
|---|---|---|
| `trainer` | `trainer123` | Entrenador (se crea siempre) |
| `cliente` | `cliente123` | Solo perfil `local`: plan Premium con rutina, dieta, seguimientos, entrenos y chat |
| `carlos` | `carlos123` | Solo perfil `local`: plan Basic, pago vencido, sin rutina |

## Tests

```powershell
.\mvnw.cmd test
```

Tests de integración (`*IT`) con Spring Boot + H2 en modo PostgreSQL, aplicando las mismas migraciones
Flyway que producción: auth, clientes, suscripciones, cobros, mensajería, workout log, fotos,
reseñas, tareas/revisiones, dashboards y seguridad (IDOR en PDFs, acceso entre clientes).

## Modo test: pagos y emails

- **Pagos** (`PAYMENTS_MODE=test`): `TestPaymentGateway` valida los datos como una pasarela real pero no mueve dinero.
  Nunca se guarda el número de tarjeta ni el CVC (solo marca y últimos 4 dígitos).
  - Tarjeta `4242 4242 4242 4242` → aprobada (y cualquier otra tarjeta válida)
  - Tarjeta `4000 0000 0000 0002` → rechazada · `4000 0000 0000 9995` → fondos insuficientes
  - Bizum: cualquier móvil español → aprobado, salvo `600 000 000` → rechazado
  - Efectivo: solo lo registra el entrenador.
  Para usar una pasarela real basta con implementar `PaymentGateway` (Stripe, Redsys…).
- **Emails** (`MAIL_MODE=test`): se generan y guardan en la bandeja de salida (sección *Emails* del entrenador),
  no se envían. Con `MAIL_MODE=smtp` y `MAIL_HOST/MAIL_PORT/MAIL_USERNAME/MAIL_PASSWORD` se envían por SMTP.

Emails automáticos: bienvenida con contraseña temporal, restablecimiento de contraseña, cobro pendiente,
recibo de pago, cambios de suscripción, feedback del seguimiento y recordatorio de revisión.

## Captación de clientes nuevos (solicitudes)

Flujo: **solicitud → cuestionario → alta → activación**.

1. Formulario público `POST /api/public/leads` (nombre, email, teléfono, objetivo, plan de interés,
   consentimiento). Protección anti-spam: campo trampa (*honeypot*), máx. 10 envíos/hora por IP y sin
   duplicados para un email con una solicitud abierta. Respuesta siempre igual (no revela si el email existe).
   Emails: acuse al interesado y aviso al entrenador (`LEADS_NOTIFY_EMAIL` o el email de los entrenadores) + push.
2. El entrenador envía el **cuestionario** (enlace personal, caduca en 14 días; solo se guarda el SHA-256 del
   token) o **rechaza** con un email de «lo sentimos» y mensaje personal opcional.
3. El interesado responde (salud, lesiones, entrenamiento, alimentación, hábitos, plan y periodo) con
   **consentimiento explícito para datos de salud**. Aviso al entrenador (resalta lesiones/condiciones).
4. El entrenador **acepta** (plan, periodo y precio especial) → se crean cuenta, suscripción y primer cobro, y se
   envía un email de **activación** (enlace de 7 días) donde el cliente elige su contraseña y entra directamente;
   o **rechaza**. El cuestionario queda en la ficha del cliente y las notas clave (lesiones, alergias) en sus notas.
5. Las solicitudes se pueden borrar (RGPD) con sus respuestas de salud.

Estados: `NEW`, `QUESTIONNAIRE_SENT`, `QUESTIONNAIRE_COMPLETED`, `ACCEPTED`, `REJECTED` (tabla `leads`, Flyway V7).

## Vídeos de técnica

El cliente graba un ejercicio con el móvil (o lo sube de la galería) para que el entrenador le corrija la
técnica; el entrenador responde con una corrección escrita y también puede enviar vídeos de ejemplo a
cada cliente. Ambos reciben notificación push.

- Formatos: MP4, MOV (iPhone) y WEBM, validados por la firma del archivo; máximo `VIDEOS_MAX_SIZE_MB` (200 MB).
- Se guardan en el almacenamiento privado (`videos/{clientId}/…`) y se reproducen con **enlaces firmados de
  corta duración** (HMAC, 3 h): `<video>` no puede enviar el JWT y el token no debe ir en URLs.
  El streaming admite peticiones `Range` (206), necesarias para avanzar en el vídeo y para Safari/iOS.
- Al borrar un cliente se borran sus vídeos. Detrás de un proxy (nginx) sube `client_max_body_size` a 210m.

## Notificaciones push (PWA)

Web Push estándar (VAPID + cifrado `aes128gcm`, RFC 8291/8292), sin servicios de terceros de pago:
funciona con Chrome/Edge/Firefox en Android y escritorio y con Safari en iPhone/iPad (iOS 16.4+ con la
app **instalada** en la pantalla de inicio). Las claves VAPID se generan la primera vez y se guardan en
`app_settings`; en producción se pueden fijar con `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY`.

| Evento | Destinatario | Al tocarla abre |
|---|---|---|
| Vídeo de técnica del cliente | Entrenador | Vídeos del cliente |
| Corrección de técnica / vídeo de ejemplo | Cliente | Técnica |
| El entrenador escribe | Cliente | Mensajes |
| Un cliente escribe | Entrenador | Conversación del cliente |
| Cobro pendiente / recordatorio de pago vencido (días 1, 3, 7 y 14) | Cliente | Suscripción y pagos |
| Pago recibido | Entrenador | Cobros del cliente |
| Toca revisión | Cliente | Seguimiento |
| Seguimiento enviado | Entrenador | Seguimientos |
| Feedback del seguimiento | Cliente | Seguimiento |

Se envían después del commit y en segundo plano (no retrasan la petición). Las suscripciones caducadas
(404/410) se borran solas; al cerrar sesión el dispositivo se desvincula de la cuenta.

## Passkeys (Face ID, huella, Windows Hello)

WebAuthn con credenciales *discoverable* y verificación de usuario obligatoria (Yubico `webauthn-server-core`).
Tras entrar con contraseña la app ofrece crear una passkey; después se entra con el botón
«Entrar con Face ID / huella» o desde el autocompletado del campo usuario. Cada usuario gestiona sus
passkeys en *Perfil* (cliente) o *Ajustes* (entrenador). En producción hay que configurar el dominio:
`WEBAUTHN_RP_ID=adrifit.es` y `WEBAUTHN_ORIGINS=https://adrifit.es` (requiere https).

## Tareas programadas

Cada día (`JOBS_DAILY_CRON`, por defecto 06:00) se ejecuta:
1. **Renovaciones**: cada suscripción activa cuyo periodo ha vencido genera el cobro del mes siguiente;
   si el cliente la canceló, se cierra sin cobrar.
2. **Revisiones**: crea una tarea de revisión para el entrenador y envía un recordatorio al cliente
   cuando le toca revisión según la frecuencia de su plan. Dar feedback a su seguimiento la completa.
3. **Recordatorios de pago**: notificación push a los clientes con cobros vencidos (días 1, 3, 7 y 14).

El entrenador también puede lanzarlas desde la app (`POST /api/jobs/daily/run`).

## Configuración (variables de entorno)

| Variable | Defecto | Descripción |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/adrifit` / `adrifit` / `adrifit` | Base de datos |
| `SERVER_PORT` | `8080` | |
| `JWT_SECRET` | clave de desarrollo | **Cámbiala en producción** (Base64, 256 bits) |
| `JWT_EXPIRATION_MS` | `86400000` | 24 h |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:[*],http://127.0.0.1:[*]` | Orígenes del frontend |
| `PUBLIC_URL` | `http://localhost:4200` | URL del frontend (enlaces de los emails) |
| `STORAGE_BASE_DIR` | `uploads` | Fotos y analíticas |
| `MAIL_MODE` | `test` | `test` o `smtp` |
| `MAIL_FROM`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | | SMTP |
| `PAYMENTS_MODE` | `test` | Pasarela de pagos |
| `JOBS_DAILY_CRON` | `0 0 6 * * *` | Tareas diarias |
| `LEADS_NOTIFY_EMAIL` | (vacío) | Email que recibe los avisos de solicitudes nuevas |
| `VIDEOS_MAX_SIZE_MB` | `200` | Tamaño máximo de un vídeo de técnica |
| `PUSH_ENABLED` | `true` | Notificaciones push |
| `PUSH_SUBJECT` | `mailto:hola@adrifitt.app` | Contacto VAPID |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | generadas y guardadas en BD | Claves Web Push |
| `WEBAUTHN_RP_ID` | `localhost` | Dominio de las passkeys |
| `WEBAUTHN_ORIGINS` | `http://localhost:4200,http://localhost:4300` | Orígenes permitidos |
| `SEED_DEMO_DATA` | `false` (`true` en perfil `local`) | Datos de demo |

## API (resumen)

Todas las rutas protegidas requieren `Authorization: Bearer <token>`.

| Módulo | Entrenador | Cliente |
|---|---|---|
| Auth | `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/change-password` | ídem |
| Clientes | `GET/POST /api/clients`, `GET/PUT/DELETE /api/clients/{id}`, `POST /api/clients/{id}/reset-password` | `GET /api/clients/me` |
| Suscripciones | `PUT /api/clients/{id}/subscription`, `PATCH …/subscription/status`, `GET …/subscription(/history)` | `GET /api/subscriptions/me(/history)`, `POST …/me/change-plan`, `…/me/cancel`, `…/me/resume` |
| Cobros | `GET/POST /api/payments`, `GET /api/payments/summary`, `POST /api/payments/{id}/cash|cancel|refund` | `GET /api/payments/me`, `POST /api/payments/me/{id}/card|bizum` |
| Mensajes | `GET /api/messages/conversations`, `GET/POST /api/messages/clients/{id}`, `…/read`, `GET /api/messages/unread-count` | `GET/POST /api/messages/me`, `POST …/me/read`, `GET …/me/unread-count` |
| Workout log | `GET /api/clients/{id}/workout-logs(/progress)` | `GET /api/workout-logs/me/days`, `…/me/template?dayNumber=`, CRUD `/api/workout-logs/me`, `…/me/progress` |
| Fotos | `GET /api/clients/{id}/photos`, `PATCH /api/photos/{id}/comment` | `POST/GET /api/photos/me`, `DELETE /api/photos/me/{id}`; contenido: `GET /api/photos/{id}/content` |
| Tareas y revisiones | CRUD `/api/tasks`, `PATCH …/{id}/complete|reopen`, `GET /api/reviews/schedule`, `POST /api/jobs/daily/run` | — |
| Emails | `GET/POST /api/emails`, `GET /api/emails/{id}` | — |
| Reseñas | `GET /api/testimonials`, `PATCH /api/testimonials/{id}/visibility` | `GET/POST /api/testimonials/me` · público: `GET /api/public/testimonials` |
| Solicitudes | `GET /api/leads(?status=)`, `GET /api/leads/counts`, `GET /api/leads/{id}`, `POST …/{id}/questionnaire|reject|approve|resend-activation`, `PATCH …/{id}/note`, `DELETE …/{id}`, `GET /api/clients/{id}/questionnaire` | público: `POST /api/public/leads`, `GET/POST /api/public/questionnaire/{token}`, `GET/POST /api/public/activation/{token}` |
| Vídeos de técnica | `GET /api/videos(?pending=true)`, `GET /api/videos/counts`, `GET/POST /api/clients/{id}/videos`, `PATCH /api/videos/{id}/feedback`, `DELETE /api/videos/{id}` | `GET/POST /api/videos/me`, `POST /api/videos/me/seen`, `DELETE /api/videos/{id}` (los suyos) · ambos: `GET /api/videos/{id}/link` → `…/stream?exp&sig` |
| Push | `GET /api/push/public-key`, `GET /api/push/status`, `POST /api/push/subscriptions(/remove)`, `POST /api/push/test` | ídem |
| Passkeys | `GET /api/passkeys`, `POST /api/passkeys/register/options|finish`, `DELETE /api/passkeys/{id}` · login: `POST /api/auth/passkey/options|finish` | ídem |
| Paneles | `GET /api/dashboard/trainer`, `GET /api/dashboard/business` | `GET /api/dashboard/client` |
| Rutinas, dietas, planes, seguimientos, analíticas | endpoints existentes (`/api/workouts`, `/api/trainer/diets`, `/api/plans`, `/api/reports`, `/api/analyses`) | ídem en su versión de cliente |

Errores: `{ status, error, message, path, fieldErrors }` — 400 validación, 401 sin sesión, 402 pago rechazado,
403 sin permiso o funcionalidad no incluida en el plan, 404, 409 regla de negocio, 413 archivo demasiado grande.
