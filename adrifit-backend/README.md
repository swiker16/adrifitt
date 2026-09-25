# AdriFit Backend

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

Requiere JDK 21 (`JAVA_HOME`). No hace falta instalar Maven.

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

## Tareas programadas

Cada día (`JOBS_DAILY_CRON`, por defecto 06:00) se ejecuta:
1. **Renovaciones**: cada suscripción activa cuyo periodo ha vencido genera el cobro del mes siguiente;
   si el cliente la canceló, se cierra sin cobrar.
2. **Revisiones**: crea una tarea de revisión para el entrenador y envía un recordatorio al cliente
   cuando le toca revisión según la frecuencia de su plan. Dar feedback a su seguimiento la completa.

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
| Paneles | `GET /api/dashboard/trainer`, `GET /api/dashboard/business` | `GET /api/dashboard/client` |
| Rutinas, dietas, planes, seguimientos, analíticas | endpoints existentes (`/api/workouts`, `/api/trainer/diets`, `/api/plans`, `/api/reports`, `/api/analyses`) | ídem en su versión de cliente |

Errores: `{ status, error, message, path, fieldErrors }` — 400 validación, 401 sin sesión, 402 pago rechazado,
403 sin permiso o funcionalidad no incluida en el plan, 404, 409 regla de negocio, 413 archivo demasiado grande.
