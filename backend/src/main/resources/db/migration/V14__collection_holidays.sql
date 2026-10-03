-- Holiday shifts, the one thing V12 left out.
--
-- V12 turned the calendar into patterns so it would stop running out. A
-- pattern is right about every ordinary week and wrong about the handful of
-- days that matter most: when a collection lands on a statutory holiday the
-- city moves it, and a resident who trusts the unshifted date puts a bin out
-- on a day nobody comes. Being wrong on the normal weeks is a bug; being
-- wrong on Christmas is the bug residents remember.
--
-- So exceptions get their own table rather than hand-edited collection_event
-- rows. Rows would work exactly once: CollectionCalendarTopUp materializes
-- the horizon forward for ever, and next year's Christmas would need another
-- person to remember again. That is the failure V12 existed to delete.

create table collection_holiday (
    id bigint primary key auto_increment,
    holiday_date date not null,
    name_fr varchar(200) not null,
    name_en varchar(200) not null,
    name_zh varchar(200) not null,
    -- How far the collection moves when it lands on this date. A tinyint
    -- rather than a boolean because municipalities differ: some push the one
    -- collection to the next day, some shift the rest of the week.
    --
    -- The CHECK is not paranoia about typos. The shift is applied in a loop -
    -- a date that moves onto another holiday moves again - and a zero or
    -- negative shift turns that loop into one that never ends. The database
    -- refuses the row, and HolidayShift clamps as well, because a constraint
    -- that only exists in one of the two places is one migration away from
    -- existing in neither.
    shift_days tinyint not null default 1,
    check (shift_days between 1 and 7),
    -- Which sectors observe it. 'all' is the normal case; the column exists
    -- because a sector-scoped exception (a street closure, a local event) is a
    -- real thing an administrator will eventually need to express.
    sector varchar(20) not null default 'all',
    -- Null means nobody has checked this against the city's own calendar yet.
    -- The seed below is deliberately left null for exactly that reason.
    source_url varchar(1000),
    active boolean not null default true,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    unique key uq_collection_holiday_date_sector (holiday_date, sector)
);

-- The fixed-date statutory holidays in Quebec, as a starting pattern.
--
-- Read the null source_url as what it is: UNVERIFIED. Which holidays Blainville
-- actually shifts collection for, and by how much, is municipal policy printed
-- on the city's collection calendar, and nobody has put that document next to
-- this table yet. Quebec also has four moving statutory holidays - Good
-- Friday or Easter Monday, the Journee nationale des patriotes, Labour Day and
-- Thanksgiving - which are deliberately absent rather than computed here: a
-- date this table is unsure about is worse than a date it does not claim.
--
-- This is the same honesty the schedule rules carry. docs/design-notes.md says
-- the patterns are illustrative rather than imported, and these are too.
insert into collection_holiday (holiday_date, name_fr, name_en, name_zh, shift_days, sector) values
('2026-12-25', 'Noel', 'Christmas Day', '圣诞节', 1, 'all'),
('2027-01-01', 'Jour de l''An', 'New Year''s Day', '元旦', 1, 'all'),
('2027-06-24', 'Fete nationale du Quebec', 'Quebec National Holiday', '魁北克省庆日', 1, 'all'),
('2027-07-01', 'Fete du Canada', 'Canada Day', '加拿大国庆日', 1, 'all'),
('2027-12-25', 'Noel', 'Christmas Day', '圣诞节', 1, 'all');
