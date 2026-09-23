# Blainville Waste Sorting Platform

A website that tells residents of Blainville, Quebec which bin to put out tonight and
where any item belongs.

**Try it: <https://blainville-waste-sorting.onrender.com/>**
(It runs on a free server that sleeps when nobody is using it, so the first visit can take
about a minute to load.)

<img src="docs/verification/screenshots/01-home-fr.png" width="600" alt="Home page showing the next collection" />

## The problem

When you move to Blainville, taking out the garbage is surprisingly confusing:

- **Which bin goes out tonight?** It depends on which side of boulevard de la Seigneurie
  you live on, and on the week.
- **Where does this item go?** The rules are local. A greasy pizza box goes in the brown
  (compost) bin here, but in the garbage in many other cities. Batteries and paint go to the
  ecocentre, not in any bin.
- **What about big or seasonal items?** Furniture, branches and Christmas trees each have
  their own pickup dates and sign-up rules.

All of this is spread across several pages of the city's website. Many newcomers end up
watching which bins their neighbours put out. Putting out the wrong bin, or missing a pickup,
means garbage sitting at home for another week or two.

## Who it's for

- **Residents**, especially people who just moved to Blainville.
- **City staff** who need to keep the schedule, the sorting rules and public notices up to
  date.

## What you can do with it

### As a resident

1. **See tonight's bin at a glance.** Choose your side of the city once. The home page then
   shows which bin goes out next, when to put it out (after 8 p.m. the night before) and when
   to bring it back.
2. **Look up any item.** Type "pizza box", "battery" or "old sofa" and get the right bin or
   drop-off location, with instructions.
3. **Ask in your own words.** Not sure what an item is called? Ask a question like "Where
   does a dirty pizza box go?" The answer shows which part of
   the city's guide it came from, so you can check it.
4. **Take a photo.** If you don't know what to call something, photograph it. The site works
   out what it is, then looks it up in the city's guide.
5. **Get notices.** Holiday delays and special pickups show up on your home page.

<p>
<img src="docs/verification/screenshots/10-assistant-fr.png" width="290" alt="Assistant answering a question about a dirty pizza box" />
<img src="docs/verification/screenshots/17-photo-answer.png" width="290" alt="A photo recognised as a pizza box and answered from the guide" />
</p>

### As city staff

1. **Keep everything current from one place.** Update the collection calendar, add or fix
   sorting rules, and publish notices, without a developer.
2. **Describe a change in one sentence.** For example: "Thursday's compost pickup is moved
   to Friday, tell residents." The AI assistant prepares the calendar change and a notice,
   then waits. Nothing changes until a staff member reviews it and clicks
   approve.
3. **See what residents are asking that the guide doesn't cover.** For example, if many
   people asked about aquariums last month, staff know what to add next.

<p>
<img src="docs/verification/screenshots/13-agent-plan.png" width="290" alt="AI assistant proposing a schedule change, waiting for approval" />
<img src="docs/verification/screenshots/14-agent-applied.png" width="290" alt="The change applied after a staff member approved it" />
</p>

## Why it works this way

- **The assistant never guesses.** A wrong answer about garbage is worse than no answer,
  because the resident trusts it and puts the item in the wrong bin. So the assistant answers
  only from the city's own guide. If the guide doesn't cover a question, it tells the
  resident to check the city's website, instead of making something up.
- **For photos, the AI only names the object. The city's guide decides the bin.** A general
  AI would answer from recycling rules in general, and Blainville's rules are not general.
- **The AI can't change anything by itself.** Staff always see exactly what will change and
  approve it first. One schedule mistake could send a whole neighbourhood out with the wrong
  bin.
- **Photos are cleaned before they are stored.** Phone photos contain the GPS location where
  they were taken, often the resident's own home. That location is removed.

## What it achieves

- **Accurate answers.** In testing, the assistant answered all 12 questions the city's guide
  covers correctly, and declined all 6 questions it doesn't cover instead of guessing.
- **No double changes.** In an early test, two staff members approved the same change at
  the same moment, and it was applied twice. That is fixed: each approved change is applied
  exactly once.
- **Keeps working when something breaks.** If one of the services behind the site goes down,
  pages still load normally instead of freezing.
- **Handles busy moments.** With 200 simulated residents using the site at the same time,
  every request succeeded.
- **Thoroughly tested.** 180 automated tests check that it behaves correctly, many of them
  against the same kind of setup it runs on in real life.

## About this project

This is a personal portfolio project. It is not an official City of Blainville service, so
always check collection dates on blainville.ca. It was built with AI assistance (Claude Code),
and commits co-written by Claude are marked in the history.

---

**For engineers:** architecture, design decisions, test details and how to run it locally are in
[TECHNICAL.md](TECHNICAL.md). Built with Java 21, Spring Boot, Vue 3 and MySQL.
