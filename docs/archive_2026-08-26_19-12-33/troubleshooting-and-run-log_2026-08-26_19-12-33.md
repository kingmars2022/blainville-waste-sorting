# Troubleshooting and Run Log

Generated at: `2026-08-26 19:12:33`

## MySQL Workbench

The user opened MySQL Workbench and connected to a local MySQL instance.

The database was created with:

```sql
CREATE DATABASE bienvenue_blainville
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

When the user attempted to create the database again, MySQL returned:

```text
Error Code: 1007. Can't create database ... database exists
```

This was expected and harmless because the database had already been created.

## Root Password Issue

The backend initially failed to start because it attempted to connect as `root` without a password.

Important error:

```text
Access denied for user 'root'@'localhost' (using password: NO)
```

Resolution:

- Use a dedicated project database user instead of root.
- Configure credentials through environment variables.
- Avoid writing real passwords into `application.yml`.

## Maven Version Issue

The user's terminal used Maven 3.6.1:

```text
Apache Maven 3.6.1
```

The initial `maven-compiler-plugin` version required Maven 3.6.3 or newer.

Resolution:

- Downgraded `maven-compiler-plugin` from `3.13.0` to `3.11.0`.

## Environment Variable Configuration

The backend database configuration was updated to read from environment variables:

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/bienvenue_blainville?useUnicode=true&characterEncoding=utf8&serverTimezone=America/Toronto}
    username: ${DB_USERNAME:blainville_app}
    password: ${DB_PASSWORD:}
```

JWT secret configuration also uses an environment variable:

```yaml
app:
  jwt:
    secret: ${APP_JWT_SECRET:change-this-development-secret-change-before-production}
```

## Local Backend Startup

Manual backend startup uses:

```bash
DB_PASSWORD='your_local_password' mvn spring-boot:run
```

Expected success output:

```text
Tomcat started on port 8080
Started BienvenueBlainvilleApplication
```

## Docker Compose Setup

Docker Compose was added for:

- MySQL
- Spring Boot backend

The frontend remains local and is started with Vite.

Docker command:

```bash
docker compose up --build
```

If this is run outside the project root, Docker returns:

```text
no configuration file provided: not found
```

Resolution:

```bash
cd /Users/siguangzhao/Documents/GitHub/my-projects/projects/Bienvenue_à_Blainville
docker compose up --build
```

## Docker Backend Jar Issue

Docker initially built the backend image but the backend container exited with:

```text
no main manifest attribute, in app.jar
```

Cause:

- The jar was not repackaged as a Spring Boot executable jar.

Resolution:

- Added `spring-boot-maven-plugin` `repackage` execution in `backend/pom.xml`.

After the fix, Docker Compose successfully started:

```text
bienvenue-blainville-mysql      Up ... healthy
bienvenue-blainville-backend    Up ...
```

Backend logs confirmed:

```text
Successfully applied 5 migrations to schema `bienvenue_blainville`
Tomcat started on port 8080
Started BienvenueBlainvilleApplication
```

## Frontend Startup

Frontend command:

```bash
cd /Users/siguangzhao/Documents/GitHub/my-projects/projects/Bienvenue_à_Blainville/frontend
npm run dev
```

Frontend URL:

```text
http://localhost:5173
```

## Validation Commands Used

Backend build:

```bash
mvn -q -DskipTests package
```

Frontend build:

```bash
npm run build
```

Docker Compose config validation:

```bash
docker compose --env-file .env.example config
```

Docker status:

```bash
docker compose ps
```

Docker backend logs:

```bash
docker compose logs --tail=80 backend
```

