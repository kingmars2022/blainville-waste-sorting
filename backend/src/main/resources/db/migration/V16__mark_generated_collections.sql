-- Tell a generated collection apart from one an administrator typed.
--
-- Until now nothing could. Both arrive in collection_event with the same
-- columns, so any operation that rebuilds the calendar has to delete both or
-- neither. V15 deleted both - bluntly, on purpose, to clear a calendar that
-- was naming the wrong bin - and it would have taken an administrator's
-- hand-entered collection with it. That was acceptable exactly once, for a
-- known-bad calendar, and is not acceptable as the way the application
-- behaves every time somebody adds a holiday.
--
-- So generation is recorded. CollectionCalendarTopUp writes `true`, the admin
-- create path leaves the default `false`, and a rebuild deletes only rows it
-- is entitled to delete.

-- `generated` on its own is a reserved word in MySQL 8 (GENERATED ALWAYS AS),
-- so the column is named for what produced the row rather than fighting the
-- parser with backticks every time it is read.
alter table collection_event
    add column auto_generated boolean not null default false;

-- Backfill, bounded by a fact rather than a guess: V15 deleted every row from
-- 2026-10-05 onward and let the top-up re-materialize them, so every row at or
-- after that date was produced by generation. Nothing is claimed about earlier
-- rows - they are the V2/V6 seed and whatever was entered by hand before this
-- column existed, they are in the past, and a rebuild never reaches backwards
-- into them.
update collection_event set auto_generated = true where collection_date >= '2026-10-05';
