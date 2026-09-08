# AdriFit Backend

Backend REST API para la gestión de clientes de un entrenador personal.

## Stack

- Java 21
- Spring Boot 3.5
- Spring Security + JWT
- Spring Data JPA
- PostgreSQL
- Maven

## Arquitectura

Monolito modular organizado por features. Cada feature tiene sus propias capas:

```
com.adrifit.backend
├── auth        # Login + JWT
├── user        # Entidad User, roles
├── client      # Gestión de clientes
├── report      # Reportes semanales
├── dashboard   # Dashboards trainer/client
└── common      # security, config, exception (transversal)
```

Cada feature sigue separación por capas: `controller` -> `service` -> `repository` -> `domain`, con `dto` y `mapper` para no exponer entidades.

## Requisitos previos

- JDK 21
- PostgreSQL en ejecución

Crea la base de datos:

```sql
CREATE DATABASE adrifit;
CREATE USER adrifit WITH PASSWORD 'adrifit';
GRANT ALL PRIVILEGES ON DATABASE adrifit TO adrifit;
```

## Configuración

Variables de entorno (con valores por defecto en `application.yml`):

| Variable | Defecto |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/adrifit` |
| `DB_USERNAME` | `adrifit` |
| `DB_PASSWORD` | `adrifit` |
| `SERVER_PORT` | `8080` |
| `JWT_SECRET` | (clave de desarrollo, **cámbiala en producción**) |
| `JWT_EXPIRATION_MS` | `86400000` (24h) |

## Ejecutar

El proyecto incluye **Maven Wrapper**, así que no necesitas instalar Maven. Solo requiere `JAVA_HOME` apuntando al JDK 21:

```powershell
# PowerShell (Windows)
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.11"
.\mvnw.cmd spring-boot:run
```

Si tienes Maven instalado globalmente también puedes usar `mvn spring-boot:run`.

Al arrancar se crea un usuario TRAINER por defecto:

- usuario: `trainer`
- contraseña: `trainer123`

## Endpoints

### Auth (público)
- `POST /api/auth/login` — `{ username, password }` -> `{ token, userId, username, role }`

### Clientes (TRAINER)
- `POST /api/clients` — crea usuario CLIENT + perfil
- `GET /api/clients`
- `GET /api/clients/{id}`
- `PUT /api/clients/{id}`
- `DELETE /api/clients/{id}`

### Reportes semanales
- `POST /api/reports` (CLIENT) — envía reporte propio
- `GET /api/reports/me` (CLIENT) — sus reportes
- `GET /api/reports/client/{clientId}` (TRAINER)

### Dashboard
- `GET /api/dashboard/trainer` (TRAINER)
- `GET /api/dashboard/client` (CLIENT)

## Autenticación

Envía el token en cada petición protegida:

```
Authorization: Bearer <token>
```
