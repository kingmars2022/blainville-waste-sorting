# Product Notes

## Decisions

- The first version is web-only.
- There are only two account roles: `ADMIN` and `USER`.
- Email and password is the first login method.
- Users manually choose `north` or `south` of boulevard de la Seigneurie after login.
- French is the default UI language. English and Chinese are secondary languages.
- Page-level reminders are required in v1.
- Native Apple/iOS push notifications are not part of v1.
- Admin pages are required in v1 so collection data and translations can be maintained.
- There is no guest account mode in v1. Users must sign in before using the app.

## Main User Jobs

- Know whether a bin must be placed outside tonight.
- Know which bin color is collected today, tomorrow, and in the next 14 days.
- Search for an item and know whether it belongs in the brown, blue, or black bin, or needs ecocentre, bulky pickup, or another special service.
- Keep the same area, language, and reminder preferences after login.

## Open Questions

- Should public registration be open, or should the admin create user accounts first?
- Should user email addresses require verification in v1?
- What exact admin email should be seeded as the first `ADMIN` account?
- Do you want the domain and UI brand to keep the exact underscore style `Bienvenue_a_Blainville`, or display it as `Bienvenue a Blainville`?
- Should new user registration be public, or should only the admin create user accounts?
