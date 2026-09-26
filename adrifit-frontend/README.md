# AdriFitt Frontend

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
(“Añadir a pantalla de inicio”), arranca sin conexión y cachea la landing y los planes.

Para probar la PWA completa en local (service worker, notificaciones push, instalación):

```bash
npm run start:pwa   # build + http://localhost:4300 con proxy de /api a :8080 (API_PORT para cambiarlo)
```

`ng serve` no activa el service worker, así que las notificaciones solo funcionan con la build.

### Marca

Logo en `public/brand/` (cabecera, login, landing, emails) y en `public/icons/` (iconos de la PWA,
*maskable* y Apple); `favicon.ico` con varios tamaños. Los PDF usan `adrifit-backend/src/main/resources/branding/logo.png`.

### Vídeos de técnica

- Cliente: menú **Técnica** (o el icono de cámara junto a cada ejercicio de *Mi rutina*). En el móvil
  permite **grabar** con la cámara trasera o elegir de la galería; muestra la vista previa y el progreso de subida.
- Entrenador: **Vídeos de técnica** con la bandeja *Por corregir*, filtro por cliente, corrección escrita y
  «Enviar vídeo a un cliente» para mandar ejemplos.
- El reproductor pide el enlace firmado solo al pulsar play (la lista no descarga vídeos) y ofrece descargarlo
  si el navegador no soporta el formato (p. ej. HEVC de iPhone en algunos PC).

### Notificaciones y passkeys

- Al entrar, la app ofrece **crear una passkey** (Face ID / Touch ID, huella en Android, Windows Hello)
  y después **activar las notificaciones**. «Ahora no» lo vuelve a preguntar en 7 días; «No volver a
  preguntar» lo oculta en ese dispositivo. Todo se gestiona luego en *Perfil* (cliente) o *Ajustes* (entrenador).
- En el login: botón «Entrar con Face ID / huella» y passkeys en el autocompletado del campo usuario.
- **iPhone/iPad**: las notificaciones web requieren iOS 16.4+ y abrir AdriFitt desde la pantalla de inicio;
  la app lo explica si se abre desde Safari.
- Passkeys y push necesitan **https** en producción (en `localhost` funcionan sin él).

## Estructura

```
src/app
├── core           auth (JWT), guards, interceptor, servicios HTTP por módulo, push, passkeys
├── shared         modelos (contratos de la API), componentes (gráficos SVG, imagen autenticada), utilidades
├── layouts        layout de entrenador y de cliente (menú lateral, badges, barra inferior móvil)
└── features
    ├── landing    web pública: planes y reseñas de clientes
    ├── auth       login
    ├── trainer    dashboard, negocio, clientes, mensajes, revisiones y tareas, seguimientos,
    │              vídeos de técnica, analíticas, rutinas, dietas, planes, cobros, emails, reseñas, ajustes
    └── client     panel, rutina, registrar entreno, técnica (vídeos), dieta, seguimiento, progreso,
                   fotos, analíticas, mensajes, suscripción y pagos, reseña, perfil
```

## Usuarios de prueba (backend con perfil `local`)

| Usuario | Contraseña | Rol |
|---|---|---|
| `trainer` | `trainer123` | Entrenador |
| `cliente` | `cliente123` | Cliente Premium (chat, PDF, revisión semanal) |
| `carlos` | `carlos123` | Cliente Basic (sin chat) con un pago vencido |

Pagos en **modo test**: tarjeta `4242 4242 4242 4242` (aprobada), `4000 0000 0000 0002` (rechazada),
Bizum con cualquier móvil salvo `600 000 000` (rechazado). Los emails se guardan en *Emails* (no se envían).
