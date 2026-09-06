# User Questions and Requirements

Generated at: `2026-08-26 19:12:33`

This file summarizes the user's questions, decisions, and requirements discussed during the project setup.

## Initial Product Idea

The user wanted to build an Apple, Android, or web application about waste sorting and collection in Blainville, Quebec.

Key concern:

- As a new resident, the user did not know which bin should be placed outside.
- The user often had to look at neighbors' bins to infer the correct collection type.
- Holiday changes and special collection rules should also be considered.

## Platform Decision

Decision:

- First version should be a web application.
- Native iOS and Android apps are not part of the first version.

Reasoning:

- Web is simpler to publish and test.
- Users can access it from iPhone, Android, or desktop.
- It can later become a PWA.

## Backend Technology Requirement

The user wanted to use:

- Spring Boot
- MyBatis

Decision:

- Use Spring Boot + MyBatis for the backend.
- Use MySQL as the first database.

## Database Discussion

The user asked about PostgreSQL vs MySQL.

Decision:

- Use MySQL first.

Reasoning:

- Easier setup for Spring Boot + MyBatis.
- More common learning materials.
- Sufficient for the current relational data model.

## Sector Selection

Original idea:

- Determine north/south sector from postal code.

Final decision:

- Do not use postal code in version 1.
- Ask the user directly whether they live north or south of boulevard de la Seigneurie.

Reasoning:

- Postal code inference may be inaccurate.
- Manual selection is simpler and clearer.

## User Accounts

Decision:

- Use email and password login.
- Do not include guest accounts.
- Use two roles:
  - `ADMIN`
  - `USER`

Admin:

- The user's own account should become the admin account in a later implementation step.

## Reminder Requirements

The user wanted reminders for:

- Which bin to place outside.
- When to place it outside.
- When collection does not happen due to holidays or special changes.

Decision:

- Page-level reminders are required.
- Apple native push notifications are not part of version 1.

Reminder examples:

- Put bins out after 20:00 the evening before collection.
- Put bins out before 06:00 on collection day.
- Bring empty bins back before 20:00 on collection day.

## Language Requirements

Decision:

- Default language: French.
- Secondary languages: English and Chinese.

Important requirement:

- A page must not mix French, English, and Chinese randomly.
- All text should switch consistently when the user changes language.

Implementation direction:

- Centralized i18n dictionaries.
- Sorting records and keywords should support multiple languages.

## Forum Discussion

The user considered making the application forum-style.

Decision:

- Do not build a forum.
- A forum would increase scope too much.
- Focus on the core web application.

## Project Name

Chosen project name:

```text
Bienvenue à Blainville
```

Project folder:

```text
/Users/siguangzhao/Documents/GitHub/my-projects/projects/Bienvenue_à_Blainville
```

## Admin Dashboard Requirement

The user asked what Admin is supposed to do because the first admin page was empty.

Admin should manage:

- Collection schedules.
- Sorting items.
- French, English, and Chinese translations.
- Special notices.
- Seasonal collection services.
- Locations and official source URLs.

Current status:

- Admin frontend prototype exists.
- Backend CRUD APIs still need to be implemented.

## Waste Sorting Data Requirement

The user asked to include:

- What goes into the brown bin.
- What goes into the blue bin.
- What goes into the black bin.
- What goes to the ecocentre.
- Special collection services.
- Seasonal or month-based collection reminders.
- Address/location information for drop-off or curbside placement.

Added categories:

- Food scraps.
- Yard waste.
- Food-soiled paper/cardboard.
- Containers and packaging.
- Printed paper.
- Household waste.
- Ecocentre items.
- Bulky items.
- Branches.
- Christmas trees.
- Personal document shredding.
- Cedar trimmings.

## Docker Requirement

The user wanted Docker added without changing the existing technology stack.

Decision:

- Keep Vue, Spring Boot, MyBatis, and MySQL.
- Add Docker Compose for MySQL and backend.
- Keep frontend local with Vite during development.

Reasoning:

- Backend and database environment become reproducible.
- Frontend local development remains fast.
- Resume can later mention DevOps and containerization accurately.

## README Requirement

The user wanted the GitHub README to be detailed and professional English.

README should explain:

- Project motivation.
- What the application does.
- Who interacts with it.
- Use cases.
- Why this architecture was chosen.
- How the software runs.
- Docker strategy.
- Data model.
- Internationalization.
- Admin workflow.
- Current status.
- Limitations.
- Roadmap.

Decision:

- README is English only.
- Resume-specific project description should not be stored in the GitHub project.

