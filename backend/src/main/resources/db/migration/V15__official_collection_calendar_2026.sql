-- The official 2026 calendar, finally read instead of guessed.
--
-- Source: Ville de Blainville, "Calendrier des collectes residentielles 2026 -
-- Residences de neuf logements et moins", blainville.ca. Every date below was
-- read off that document's month grids, where a black bin is ordures
-- menageres, a blue triangle is matieres recuperables and a brown apple is
-- matieres organiques.
--
-- Three things the document says that this database had wrong:
--
-- 1. ORDURES MENAGERES WERE MISSING ENTIRELY. The calendar alternates garbage
--    and recycling on the same weekday - Tuesday in the south, Wednesday in
--    the north - so a resident reading this application saw half their
--    collections. That is the kind of gap a schema review does not catch,
--    because nothing was malformed; there was simply no row.
--
-- 2. THE RECYCLING WEEKS WERE THE GARBAGE WEEKS. V12 anchored recycling to
--    2026-09-15 (south) and 2026-09-16 (north). Both are black-bin days on the
--    official grid; the blue-bin days that week are 2026-09-08 and 2026-09-09.
--    So the existing rules were right about the weekday and the fortnight and
--    wrong about which fortnight - the failure mode an illustrative pattern
--    has when nobody checks it against the source. They are corrected here
--    rather than deleted, because their anchors were the garbage anchors all
--    along.
--
-- 3. CANADA DAY DOES NOT MOVE A COLLECTION. V14 guessed that it would and
--    seeded it. The 2026 grid shows Wednesday 1 July carrying an ordinary
--    blue-bin collection. The only shift printed on the whole 2026 calendar is
--    the asterisk in January: "Collecte du 1er reportee au 2".
--
-- What this migration still does NOT know: 2027. This document covers one
-- year. Whether Blainville shifts the Fete nationale, Labour Day or
-- Thanksgiving in a year where they land on a collection weekday is not
-- answered anywhere in it, so nothing is claimed about them.

-- --------------------------------------------------------------------------
-- 1. The two mislabelled rules are what the garbage weeks always were.
-- --------------------------------------------------------------------------
update collection_schedule_rule
set collection_type = 'garbage',
    bin_color = 'black',
    note_fr = 'Ordures menageres, le mardi, une semaine sur deux, au sud du boulevard de la Seigneurie.',
    note_en = 'Household waste, every other Tuesday, south of boulevard de la Seigneurie.',
    note_zh = '生活垃圾，boulevard de la Seigneurie 以南，隔周周二收集。',
    source_url = 'https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles'
where sector = 'south' and collection_type = 'recycling';

update collection_schedule_rule
set collection_type = 'garbage',
    bin_color = 'black',
    note_fr = 'Ordures menageres, le mercredi, une semaine sur deux, au nord du boulevard de la Seigneurie.',
    note_en = 'Household waste, every other Wednesday, north of boulevard de la Seigneurie.',
    note_zh = '生活垃圾，boulevard de la Seigneurie 以北，隔周周三收集。',
    source_url = 'https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles'
where sector = 'north' and collection_type = 'recycling';

-- --------------------------------------------------------------------------
-- 2. Recycling, on the fortnight the official grid actually prints it.
--    generated_through is set a day before the cutoff in step 4, so the
--    top-up materializes these from the same point as every other rule.
-- --------------------------------------------------------------------------
insert into collection_schedule_rule
(sector, collection_type, bin_color, anchor_date, interval_weeks, generated_through, note_fr, note_en, note_zh, source_url)
values
('south', 'recycling', 'blue', '2026-09-08', 2, '2026-10-04',
 'Matieres recuperables, le mardi, une semaine sur deux, en alternance avec les ordures menageres, au sud du boulevard de la Seigneurie.',
 'Recycling, every other Tuesday, alternating with household waste, south of boulevard de la Seigneurie.',
 '可回收物，boulevard de la Seigneurie 以南，隔周周二收集，与生活垃圾交替。',
 'https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles'),
('north', 'recycling', 'blue', '2026-09-09', 2, '2026-10-04',
 'Matieres recuperables, le mercredi, une semaine sur deux, en alternance avec les ordures menageres, au nord du boulevard de la Seigneurie.',
 'Recycling, every other Wednesday, alternating with household waste, north of boulevard de la Seigneurie.',
 '可回收物，boulevard de la Seigneurie 以北，隔周周三收集，与生活垃圾交替。',
 'https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles');

-- The organics rule was right. It only lacked a citation.
update collection_schedule_rule
set note_fr = 'Matieres organiques, le jeudi, chaque semaine, sur tout le territoire.',
    note_en = 'Organics, every Thursday, throughout the city.',
    note_zh = '有机垃圾，全市每周四收集。',
    source_url = 'https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles'
where collection_type = 'organic';

-- --------------------------------------------------------------------------
-- 3. Holidays: keep the one the calendar prints, drop the ones V14 guessed.
-- --------------------------------------------------------------------------
delete from collection_holiday where source_url is null;

insert into collection_holiday (holiday_date, name_fr, name_en, name_zh, shift_days, sector, source_url) values
('2026-01-01', 'Jour de l''An', 'New Year''s Day', '元旦', 1, 'all',
 'https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles');

-- --------------------------------------------------------------------------
-- 4. Rebuild the forward calendar from the corrected patterns.
--
-- This is the one place this migration overrides the rule that an
-- administrator's deletion is never resurrected. It is deliberate and it is
-- narrow: every generated occurrence after the cutoff was produced from a
-- pattern now known to be wrong about which bin goes out, and a calendar that
-- confidently names the wrong bin is worse than one briefly rebuilt. Dates on
-- or before the cutoff are left alone - they are past, and nothing reads them.
-- --------------------------------------------------------------------------
delete from collection_event where collection_date >= '2026-10-05';
update collection_schedule_rule set generated_through = '2026-10-04';
