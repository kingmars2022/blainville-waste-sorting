-- The collection calendar used to be a wall of hand-written INSERT rows with an
-- end date. V2 seeded a handful, V6 extended them to 2026-10-29, and on
-- 2026-10-30 the home page would have said "no collection" to every resident,
-- for ever, with nothing in the logs to say why. Extending the rows again only
-- moves the date.
--
-- What the rows actually encode is three recurring patterns. This table stores
-- the patterns instead, and CollectionCalendarTopUp materializes occurrences
-- from them into collection_event on a rolling horizon.
--
-- Deliberately no `weekday` column: the weekday is already implied by
-- anchor_date, and two columns that can disagree about the same fact
-- eventually do.
-- Makes generation safe to repeat. The top-up writes with `on duplicate key
-- update id = id`, so a crash between inserting occurrences and advancing the
-- high-water mark costs a repeated insert rather than a duplicated collection
-- on a resident's home page. Two collections of the same type, for the same
-- sector, on the same day are a duplicate by definition - the sorting guide
-- had exactly that problem before V10.
--
-- Deliberately not preceded by a de-duplicating delete. If a database somehow
-- holds two rows for the same collection, they may carry different notes, and
-- a migration that silently picks one is a migration that loses data quietly.
-- This one fails loudly instead, with the offending row in the error, and a
-- maintainer decides which to keep.
alter table collection_event
    add constraint uq_collection_event_date_sector_type unique (collection_date, sector, collection_type);

create table collection_schedule_rule (
    id bigint primary key auto_increment,
    sector varchar(20) not null,
    collection_type varchar(40) not null,
    bin_color varchar(20) not null,
    -- A date the pattern is known to fall on. Everything else is derived:
    -- occurrences are anchor_date + n * interval_weeks.
    anchor_date date not null,
    interval_weeks tinyint not null default 1,
    -- High-water mark. The top-up only ever generates strictly after this date,
    -- which is what stops it resurrecting an occurrence an administrator
    -- deleted - a holiday cancellation has to stay cancelled.
    generated_through date not null,
    note_fr text,
    note_en text,
    note_zh text,
    source_url varchar(1000),
    active boolean not null default true,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp
);

-- The three patterns V2 and V6 were writing out by hand. generated_through is
-- set to the last date each one actually materialized, so the top-up picks up
-- exactly where the seed left off and inserts nothing twice.
insert into collection_schedule_rule
(sector, collection_type, bin_color, anchor_date, interval_weeks, generated_through, note_fr, note_en, note_zh, source_url)
values
('all', 'organic', 'brown', '2026-09-10', 1, '2026-10-29',
 'Les matieres organiques sont collectees le jeudi sur tout le territoire.',
 'Organics are collected on Thursday throughout the city.',
 '厨余和有机垃圾全市每周四收集。',
 'https://blainville.ca/services/environnement-et-voirie/matieres-organiques'),
('south', 'recycling', 'blue', '2026-09-15', 2, '2026-10-27',
 'Collecte au sud du boulevard de la Seigneurie.',
 'Collection south of boulevard de la Seigneurie.',
 'boulevard de la Seigneurie 以南区域收集。',
 'https://blainville.ca/services/environnement-et-voirie/matieres-recuperables'),
('north', 'recycling', 'blue', '2026-09-16', 2, '2026-10-28',
 'Collecte au nord du boulevard de la Seigneurie.',
 'Collection north of boulevard de la Seigneurie.',
 'boulevard de la Seigneurie 以北区域收集。',
 'https://blainville.ca/services/environnement-et-voirie/matieres-recuperables');
