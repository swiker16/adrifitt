# AdriFit Frontend

Aplicación web (PWA instalable en móvil, tablet y escritorio) de la plataforma de entrenamiento personal.
Angular 20 · componentes standalone · signals · Angular Material (iconos) · SCSS.

## Requisitos

- Node 20+ y npm
- Backend en marcha en `http://localhost:8080` (ver `../adrifit-backend/README.md`)

## Desarrollo

```bash
npm install
npm start          # ng serve → http://localhost:4200
```

`ng serve` redirige `/api` al backend mediante `proxy.conf.json`, así que no hay problemas de CORS.
La URL de la API se define en `src/environments/environment*.ts` (`/api` por defecto: mismo dominio
detrás de un proxy inverso en producción).

## Build de producción

```bash
npm run build      # dist/adrifit-frontend/browser
```

La build de producción incluye el **service worker** (`ngsw-config.json`): la app se puede instalar
(“Añadir a pantalla de inicio”), arranca sin conexión y cachea la landing y los planes. Para probar la
PWA en local sirve `dist/adrifit-frontend/browser` con cualquier servidor estático con fallback a
`index.html` y proxy de `/api`.

## Estructura

```
src/app
├── core           auth (JWT), guards, interceptor, servicios HTTP por módulo, notificaciones
├── shared         modelos (contratos de la API), componentes (gráficos SVG, imagen autenticada), utilidades
├── layouts        layout de entrenador y de cliente (menú lateral, badges, barra inferior móvil)
└── features
    ├── landing    web pública: planes y reseñas de clientes
    ├── auth       login
    ├── trainer    dashboard, negocio, clientes, mensajes, revisiones y tareas, seguimientos,
    │              analíticas, rutinas, dietas, planes, cobros, emails, reseñas
    └── client     panel, rutina, registrar entreno, dieta, seguimiento, progreso, fotos,
                   analíticas, mensajes, suscripción y pagos, reseña, perfil
```

## Usuarios de prueba (backend con perfil `local`)

| Usuario | Contraseña | Rol |
|---|---|---|
| `trainer` | `trainer123` | Entrenador |
| `cliente` | `cliente123` | Cliente Premium (chat, PDF, revisión semanal) |
| `carlos` | `carlos123` | Cliente Basic (sin chat) con un pago vencido |

Pagos en **modo test**: tarjeta `4242 4242 4242 4242` (aprobada), `4000 0000 0000 0002` (rechazada),
Bizum con cualquier móvil salvo `600 000 000` (rechazado). Los emails se guardan en *Emails* (no se envían).
