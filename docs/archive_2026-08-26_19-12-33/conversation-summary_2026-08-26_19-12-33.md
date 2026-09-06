# Conversation Summary

Generated at: `2026-08-26 19:12:33`

This file summarizes the full project discussion in chronological order.

## 1. Product Direction

The discussion started with whether the project should be built as an Apple app, Android app, or web app. The chosen direction was a web app because it is simpler to build, easier to share, and works across mobile and desktop devices.

The project goal became a resident-facing tool for Blainville waste collection and sorting.

## 2. Main User Problem

The core problem came from the user's real living situation: after moving to Blainville, it was unclear which bin should be placed outside on a given day. The user described relying on neighbors' bins to guess whether brown, blue, or black collection was happening.

This became the central product use case: help a resident know what to do before collection day.

## 3. Technology Stack

The user wanted to use Spring Boot and MyBatis. MySQL was selected after discussing MySQL vs PostgreSQL.

Final stack:

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Spring Boot
- Java 21
- MyBatis
- MySQL
- Flyway
- Spring Security
- Docker Compose

## 4. Region and Schedule Logic

Postal-code-based sector detection was discussed but deferred. The chosen MVP approach is manual sector selection:

- North of boulevard de la Seigneurie.
- South of boulevard de la Seigneurie.

This avoids inaccurate postal-code mapping and keeps the first version simpler.

## 5. Account and Role Decisions

The user initially discussed account persistence and admin needs.

Final decision:

- Email and password login.
- No guest account mode.
- Two roles:
  - `ADMIN`
  - `USER`

Admin will manage schedules, sorting data, translations, special notices, and location information.

## 6. Language Direction

French is the default language. English and Chinese are available options.

A strong requirement was established: when the user switches language, the full interface should switch consistently. The app should not mix French, English, and Chinese text on the same screen due to incomplete translations or hard-coded labels.

The frontend uses centralized i18n dictionaries to support this direction.

## 7. Forum Idea

The user considered making the product forum-like. This was rejected because a forum would introduce too much complexity, including posts, replies, moderation, reporting, and spam handling.

Final decision:

- No forum.
- Focus on the practical waste sorting and collection web application.

## 8. Project Creation

The project was created in:

```text
/Users/siguangzhao/Documents/GitHub/my-projects/projects/Bienvenue_à_Blainville
```

Initial backend, frontend, database migrations, and documentation were added.

## 9. MySQL Setup and Troubleshooting

The user used MySQL Workbench to create the database. A root password issue occurred because the application attempted to connect as root with no password.

Solution:

- Create a dedicated MySQL user for the project.
- Use environment variables instead of storing a password in `application.yml`.

Security decision:

- Do not commit real database credentials to GitHub.

## 10. Sorting Data and Special Services

The user asked for practical sorting content:

- What goes into brown bin.
- What goes into blue bin.
- What goes into black bin.
- What goes to the ecocentre.
- What can be placed curbside.
- Which services are month-based or seasonal.
- Where special items should be brought.

The app was expanded with sorting data and seasonal reminders.

## 11. Admin Dashboard

The user noticed that Admin did not yet do anything. A frontend admin dashboard prototype was added with management sections for schedules, sorting entries, translations, and special notices.

The admin page is not yet wired to backend persistence.

## 12. Docker Addition

The user wanted Docker added in a way that did not change the existing technology stack.

Final Docker strategy:

- Dockerize MySQL and Spring Boot backend.
- Keep Vue frontend local for faster Vite development.

Files added:

- `docker-compose.yml`
- `backend/Dockerfile`
- `backend/.dockerignore`
- `.env.example`

## 13. Docker Troubleshooting

Docker initially failed because the backend jar was not a Spring Boot executable jar:

```text
no main manifest attribute, in app.jar
```

Fix:

- Add Spring Boot Maven plugin `repackage` execution.

After the fix:

- Docker MySQL started successfully.
- Backend container started successfully.
- Flyway applied 5 migrations.

## 14. README Direction

The user wanted a detailed professional English README explaining the entire project.

The README was rewritten to include:

- Overview
- Motivation
- Target users
- Core scenarios
- Features
- Tech stack
- Architecture
- Architecture decisions
- Data model
- Internationalization
- Admin workflow
- Docker strategy
- Running locally
- Commands
- Current status
- Security notes
- Official sources
- Limitations
- Roadmap
- Engineering highlights
- Project positioning

## 15. Resume Discussion

The user asked how to describe the project on a resume. A concise 4-bullet version was recommended, but resume material was not kept in the GitHub project because the user clarified that resume text belongs in the resume, not in the repository.

