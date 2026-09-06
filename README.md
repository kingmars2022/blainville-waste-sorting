# Bienvenue à Blainville

A full-stack municipal waste sorting and collection reminder web application for residents of Blainville, Quebec.

`Bienvenue à Blainville` helps residents understand which bin to place outside, when collection happens, how common materials should be sorted, and where special items should be dropped off. The application is built with Vue 3, Spring Boot, MyBatis, MySQL, Flyway, Spring Security, and Docker Compose.

The first release is intentionally designed as a web application. Native iOS and Android apps are outside the initial scope.

## Overview

This project turns Blainville's municipal waste collection and sorting information into a resident-friendly web tool. Instead of reading multiple city pages, PDF calendars, and service descriptions, users can open one application and quickly answer practical household questions:

- Which bin should I put outside tonight?
- Is tomorrow a brown, blue, or black bin collection day?
- Does this item belong in the brown bin, blue bin, black bin, or ecocentre?
- When can branches, bulky items, or Christmas trees be placed curbside?
- Where is the ecocentre?
- Is there a special notice or seasonal service I should know about?

The product is French-first because Blainville's municipal context is French. English and Chinese are included as secondary language options to support multilingual residents and newcomers.

## Motivation

New residents often struggle with local waste collection rules. Collection schedules depend on sector, bin color, collection type, and sometimes seasonal or holiday rules. Special services such as bulky item pickup, branch collection, Christmas tree collection, document shredding, and ecocentre drop-off are even harder to understand quickly.

In practice, a new resident may rely on neighbors by checking which bin appears at the curb. This project was created to reduce that uncertainty and make local waste rules easier to follow.

The application focuses on three user needs:

- Make collection timing obvious.
- Make sorting instructions searchable.
- Keep official municipal rules maintainable through structured data.

## Target Users

The application is designed for:

- New residents who recently moved to Blainville.
- Local households that need quick collection reminders.
- Residents who are unfamiliar with municipal sorting rules.
- Multilingual users who prefer English or Chinese guidance in addition to French.
- Admin users responsible for keeping schedules, sorting rules, translations, and notices up to date.

## Core User Scenarios

1. A resident opens the app in the evening and immediately sees whether a bin should be placed outside after 20:00.
2. A user checks the next collection and sees the bin color, collection type, and reminder instructions.
3. A user searches for an item such as a pizza box, battery, paint can, broken toy, or personal documents.
4. The app shows whether the item belongs in the brown bin, blue bin, black bin, ecocentre, bulky pickup, or another special service.
5. A resident checks seasonal rules for branches, Christmas trees, bulky items, cedar trimmings, or document shredding.
6. An admin updates a collection event, adds a sorting entry, edits translations, or publishes a special notice.

## Features

### Collection Reminder

The home page is designed around the most urgent question: what should be placed outside next?

It is intended to show:

- Today's collection status.
- Tomorrow's collection status.
- The next upcoming collection.
- Bin color and collection type.
- When the bin may be placed outside.
- When the empty bin should be brought back.

Blainville's general placement guidance is represented in the app: bins should be placed after 20:00 the evening before collection or before 06:00 on collection day, and empty bins should be brought back before 20:00 on collection day.

### Sector-Based Schedule

Users select whether they live:

- North of boulevard de la Seigneurie.
- South of boulevard de la Seigneurie.

The first version uses manual sector selection instead of postal-code inference. This is simpler, more transparent, and avoids inaccurate assumptions for the MVP.

### Waste Sorting Search

The sorting page supports searchable waste sorting guidance across French, English, and Chinese.

Current categories include:

- Brown bin: food scraps, organic waste, yard waste, and food-soiled paper or cardboard.
- Blue bin: containers, packaging, cardboard, aluminum, plastic bags, and printed paper.
- Black bin: household waste that does not belong in other bins and is refused at the ecocentre.
- Ecocentre: paint, batteries, electronics, tires, aerosols, oils, polystyrene, and other special materials.
- Bulky items.
- Branches.
- Christmas trees.
- Personal document shredding.
- Cedar trimmings.

Each sorting entry can include:

- Destination or service type.
- Bin color.
- Disposal instructions.
- Example materials.
- Seasonal availability.
- Location or address.
- Official source URL.

### Seasonal and Special Collection Reminders

The app includes reminders for collection services that do not fit into regular bin pickup.

Examples:

- Bulky items require a request before the last day of the previous month.
- Branch pickup is available by request in May, June, and October.
- Christmas tree pickup requests begin in December, with collection starting in early January.
- Personal document shredding is offered at the ecocentre on specific dates.
- Cedar trimmings are handled through Arbressence.
- Ecocentre-related materials include a drop-off location.

### Location and Address Guidance

Sorting entries can include a location field. This is used for records where the user needs to know whether to place an item at the curb, at the edge of the driveway, or bring it to a facility.

Current examples include:

- Ecocentre: `302 rue Omer-DeSerres, Blainville, QC J7C 5N3`.
- Bulky items: curbside in front of the user's residence.
- Branches: curbside on the user's property, with ecocentre drop-off as an alternative.
- Christmas trees: curbside, with ecocentre drop-off as an alternative.
- Personal document shredding: ecocentre.
- Cedar trimmings: edge of the user's driveway, then contact Arbressence.

### Multilingual Interface

The interface is French-first, with English and Chinese support.

The project uses centralized i18n dictionaries so UI text is not hard-coded inside Vue components. This prevents mixed-language screens and keeps translation completeness easier to verify.

Supported interface languages:

- French.
- English.
- Chinese.

### User Preferences

The planned user preference model stores:

- Selected language.
- North or south sector.
- Reminder preference.
- Reminder time.

The current frontend includes a preference store foundation. Persistent account-based preference storage is planned through the backend.

### User Roles

The application uses two roles:

- `USER`: regular resident account.
- `ADMIN`: administrator account.

There is no guest account mode in the planned product. Users are expected to sign in to save their preferences.

### Admin Dashboard

The admin dashboard is intended to maintain:

- Collection schedules.
- Sorting entries.
- Multilingual translations.
- Special notices.
- Seasonal collection rules.
- Locations and official source links.

The current admin dashboard is a frontend prototype. It provides the management layout and form structure. The next step is to connect it to protected backend `/api/admin/**` endpoints for real create, update, and delete operations.

## Tech Stack

### Frontend

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router

### Backend

- Java 21
- Spring Boot 3
- Spring Security
- MyBatis
- Flyway

### Database

- MySQL

### DevOps and Tooling

- Docker
- Docker Compose
- Maven
- npm

## Architecture

The project uses a separated frontend and backend architecture:

```text
Browser
  |
  v
Vue 3 Single-Page Application
  |
  | HTTP / JSON
  v
Spring Boot REST API
  |
  | MyBatis mapper layer
  v
MySQL Database
```

The local Docker architecture is:

```text
Docker Compose
  |
  |-- mysql    MySQL database container
  |
  |-- backend  Spring Boot API container

Local machine
  |
  |-- frontend Vue/Vite development server
```

In the current development workflow, Docker Compose runs MySQL and the backend. The frontend remains local and is served through Vite for faster UI iteration.

## Architecture Decisions

### Why Vue 3

Vue 3 is lightweight, productive, and well-suited for a mobile-first web interface. It works well for this project because the UI is interaction-heavy but not overly complex: reminders, filters, language switching, settings, sorting cards, and admin forms.

### Why TypeScript

TypeScript improves safety for shared frontend concepts such as language codes, sorting item types, destination types, and user preference state. It helps prevent accidental mismatches in multilingual UI data.

### Why Spring Boot

Spring Boot provides a stable Java backend foundation for REST APIs, validation, security configuration, dependency injection, and database integration. It is a good fit for a project that will grow into authenticated user workflows and admin-managed data.

### Why MyBatis

MyBatis keeps SQL explicit. This is useful for a project where collection schedules, keyword search, translations, and admin filters may require clear and controllable queries. It also matches the intended Java backend learning and development direction.

### Why MySQL

MySQL is sufficient for the project's relational data model and is easy to use with Spring Boot and MyBatis. The core data is naturally relational: users, preferences, collection events, sorting records, translations, keywords, and notices.

### Why Flyway

Flyway makes the database reproducible. Schema changes and seed data are tracked as versioned migrations, so a fresh database can be initialized consistently in local development or Docker.

### Why Docker Compose

Docker Compose reduces setup friction for the backend environment. Instead of requiring every developer to configure MySQL manually, Compose starts a known MySQL version and the Spring Boot backend together.

The frontend is not containerized yet because local Vite development provides faster hot reload and simpler UI work. A production frontend container can be added later.

## Data Model

The database schema is designed around municipal collection data, multilingual sorting records, and user preferences.

Core tables:

```text
app_user
  Stores user accounts, email addresses, password hashes, and roles.

user_preference
  Stores language, sector, and reminder settings for each user.

collection_event
  Stores dated collection events, including sector, collection type, bin color, notes, and source URL.

sorting_item
  Stores the base sorting record and destination type.

sorting_item_translation
  Stores multilingual names, instructions, and locations for each sorting item.

sorting_item_keyword
  Stores searchable keywords by language.

special_notice
  Stores temporary announcements such as holiday changes, service delays, or seasonal notices.
```

Flyway migrations are stored in:

```text
backend/src/main/resources/db/migration/
```

The current migration set includes initial schema creation, seed collection data, expanded sorting records, seasonal special collection records, and location fields.

## Internationalization

Internationalization is a core product requirement, not an afterthought.

Design goals:

- French is the default language.
- English and Chinese are available as user-selected languages.
- UI text is centralized in message dictionaries.
- Components should not hard-code display copy.
- Sorting records are modeled with translations.
- Keywords are stored per language.
- A language switch should update the full experience, not only part of the page.

This avoids frustrating mixed-language states such as a French title with English instructions and Chinese buttons.

## Admin Workflow

The admin role is intended to support long-term maintenance of municipal information.

Planned admin workflows:

- Create and update collection events.
- Add or correct sorting items.
- Maintain French, English, and Chinese translations.
- Add keywords for search.
- Add seasonal service rules.
- Add or update locations and addresses.
- Publish special notices for holidays, service delays, or temporary municipal programs.
- Store source URLs for traceability.

The current admin page is a UI prototype. Backend persistence for admin operations is part of the roadmap.

## Docker Strategy

The project uses a hybrid development strategy:

- Docker Compose runs MySQL and the Spring Boot backend.
- The Vue frontend runs locally with Vite.

This keeps the backend environment reproducible without slowing down frontend development.

Docker services:

```text
mysql
  MySQL 8.4 database container.
  Exposes container port 3306 on host port 3307 by default.
  Stores data in a named Docker volume.

backend
  Spring Boot application container.
  Builds from backend/Dockerfile.
  Connects to the MySQL service through the Docker network.
  Exposes port 8080.
```

The backend waits for MySQL to become healthy before starting. Flyway runs automatically when the backend starts.

## Running Locally

### Prerequisites

Recommended:

- Docker Desktop
- Node.js
- npm

Optional for manual backend execution:

- Java 21
- Maven
- Local MySQL installation

### Option A: Docker Backend Environment

This is the recommended local workflow.

From the project root:

```bash
cd /Users/siguangzhao/Documents/GitHub/my-projects/projects/Bienvenue_à_Blainville
cp .env.example .env
```

Edit `.env` and replace placeholder values:

```text
DB_PASSWORD=replace_with_a_local_dev_password
MYSQL_ROOT_PASSWORD=replace_with_a_local_root_password
APP_JWT_SECRET=replace_with_a_long_random_development_secret
```

Start MySQL and the backend:

```bash
docker compose up --build
```

Backend URL:

```text
http://localhost:8080
```

Start the frontend in a second terminal:

```bash
cd /Users/siguangzhao/Documents/GitHub/my-projects/projects/Bienvenue_à_Blainville/frontend
npm install
npm run dev
```

Frontend URL:

```text
http://localhost:5173
```

### Option B: Manual Local Backend

Use this if you want to run MySQL and Spring Boot directly on the host machine.

Create the database in MySQL Workbench:

```sql
CREATE DATABASE bienvenue_blainville
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

Create a dedicated local database user:

```sql
CREATE USER 'blainville_app'@'localhost' IDENTIFIED BY 'your_local_password';
GRANT ALL PRIVILEGES ON bienvenue_blainville.* TO 'blainville_app'@'localhost';
FLUSH PRIVILEGES;
```

Start the backend:

```bash
cd /Users/siguangzhao/Documents/GitHub/my-projects/projects/Bienvenue_à_Blainville/backend
DB_PASSWORD='your_local_password' mvn spring-boot:run
```

Start the frontend:

```bash
cd /Users/siguangzhao/Documents/GitHub/my-projects/projects/Bienvenue_à_Blainville/frontend
npm install
npm run dev
```

## Environment Variables

Backend database settings are read from environment variables:

```yaml
DB_URL
DB_USERNAME
DB_PASSWORD
APP_JWT_SECRET
```

The default backend configuration is in:

```text
backend/src/main/resources/application.yml
```

The repository includes:

```text
.env.example
```

The real `.env` file should remain local and should not be committed.

## Commands

Start Docker backend environment:

```bash
docker compose up --build
```

Stop Docker containers:

```bash
docker compose down
```

Stop containers and remove the MySQL volume:

```bash
docker compose down -v
```

Build backend:

```bash
cd backend
mvn -q -DskipTests package
```

Build frontend:

```bash
cd frontend
npm run build
```

Start frontend development server:

```bash
cd frontend
npm run dev
```

Validate Docker Compose configuration:

```bash
docker compose --env-file .env.example config
```

## Current Status

Completed:

- Initial full-stack project structure.
- Spring Boot backend skeleton.
- Vue 3 frontend skeleton.
- MyBatis mapper foundation.
- MySQL schema design.
- Flyway migrations for schema and seed data.
- Static multilingual sorting guide data.
- Seasonal and special collection reminder data.
- Location and address support for special sorting records.
- French, English, and Chinese i18n foundation.
- Admin dashboard frontend prototype.
- Docker Compose setup for MySQL and backend.
- Backend Dockerfile.
- Environment-variable-based database configuration.
- Successful backend Maven build.
- Successful frontend Vite production build.
- Successful Docker Compose backend startup validation.

Planned or in progress:

- Email and password registration.
- Login endpoint.
- JWT authentication.
- Real `ADMIN` and `USER` authorization flow.
- User preference persistence in MySQL.
- Sorting search API.
- Collection schedule API integration in the frontend.
- Admin create, update, and delete operations.
- Complete import of official Blainville sorting records.
- Future-year collection calendar import.
- PWA manifest and service worker.
- Optional production frontend container.
- Deployment configuration.

## Security Notes

The project is still in local development. Security-sensitive areas should be completed before production use.

Current security practices:

- Real database passwords should not be committed.
- `.env.example` contains placeholders only.
- Runtime secrets are passed through environment variables.
- Admin endpoints are intended to be protected under `/api/admin/**`.
- Spring Security is included as the foundation for authentication and authorization.

Production requirements:

- Replace development secrets.
- Implement real registration and login.
- Store password hashes only.
- Use HTTPS.
- Restrict CORS.
- Protect admin operations with proper role checks.
- Review all official municipal data before public release.

## Official Sources

Initial seed data and rules are based on Blainville municipal resources:

- https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles
- https://blainville.ca/services/environnement-et-voirie/matieres-organiques
- https://blainville.ca/services/environnement-et-voirie/matieres-recuperables
- https://blainville.ca/services/environnement-et-voirie/ordures-menageres
- https://blainville.ca/services/environnement-et-voirie/ecocentre
- https://blainville.ca/services/environnement-et-voirie/encombrants

## Limitations

This project is not an official municipal website.

Current limitations:

- The sorting data is an initial structured seed, not a complete official import.
- Collection schedule data is not complete for all future dates.
- Admin forms are not yet connected to backend persistence.
- Authentication and account registration are not fully implemented yet.
- Push notifications are not implemented.
- The frontend is not yet containerized for production deployment.
- All municipal rules should be verified against official Blainville sources before public use.

## Roadmap

Short-term:

- Implement email and password registration.
- Implement login and JWT authentication.
- Persist user preferences to MySQL.
- Connect collection schedule data to the frontend.
- Connect sorting search to backend APIs.
- Connect admin forms to protected backend endpoints.

Medium-term:

- Complete the Blainville sorting guide dataset.
- Import full yearly collection calendars.
- Add holiday and service-delay notices.
- Improve admin validation for missing translations.
- Add PWA support for installation on mobile devices.

Long-term:

- Add browser notification support where practical.
- Add optional postal-code or address-based sector detection.
- Add production frontend containerization.
- Deploy the full application.
- Add automated tests for core scheduling, sorting search, and admin workflows.

## Engineering Highlights

- Full-stack municipal service application with a clear resident-facing use case.
- Frontend/backend separation with Vue 3 and Spring Boot.
- Explicit MyBatis data access layer for transparent SQL control.
- Normalized MySQL schema for schedules, sorting records, translations, keywords, notices, and user preferences.
- Flyway-managed database migrations for reproducible schema setup.
- French-first multilingual interface with English and Chinese support.
- Docker Compose environment for reproducible MySQL and backend startup.
- Admin-oriented data model designed for long-term maintenance of municipal rules.

## Project Positioning

`Bienvenue à Blainville` is a resident-facing helper tool. It is designed to make official municipal information easier to search and understand, but it does not replace Blainville's official website.

Before public deployment, all collection dates, sorting rules, locations, and service instructions should be reviewed against official municipal sources and maintained through the admin workflow.
