-- Make the database the only source of the sorting guide.
--
-- Until now the resident-facing sorting cards were rendered from a TypeScript
-- file in the frontend, while the assistant, the photo lookup and the admin
-- console all read MySQL. Two consequences, both bad: an administrator could
-- edit an entry and the page residents actually look at would not change, and
-- the two datasets had drifted apart - different wording, different counts.
--
-- The static file held two things the database did not, which is why the split
-- survived this long. Both get columns here, so nothing is lost in the move.

-- Seasonal or on-request services ("free collection in May, June and October").
-- Per language, because it is prose, not a date.
alter table sorting_item_translation
    add column availability varchar(1000) null after location;

-- "fruits, legumes, pain, pates" - the short list under each card. A table
-- rather than a delimited column: they are ordered, they are per language, and
-- an administrator edits them one at a time.
create table sorting_item_example (
    id bigint primary key auto_increment,
    item_id bigint not null,
    language_code varchar(10) not null,
    position int not null,
    example varchar(255) not null,

    foreign key (item_id) references sorting_item(id),
    -- Ordered within an item and language, and a position cannot repeat.
    unique key uq_sorting_item_example (item_id, language_code, position),
    index idx_sorting_item_example (item_id, language_code, position)
);

-- Row 4 and row 14 are the same municipal service, seeded twice: "Personal
-- documents for shredding" and "Personal document shredding". Residents saw
-- two cards for one service, and retrieval split its relevance score across
-- both, making the real entry rank lower than it should.
--
-- Row 14 is strictly richer - it carries the ecocentre address and the actual
-- conditions - so row 4's keywords are merged into it before it goes, rather
-- than throwing away search terms residents might use.
insert into sorting_item_keyword (item_id, language_code, keyword)
select 14, k.language_code, k.keyword
from sorting_item_keyword k
where k.item_id = 4
  and not exists (
      select 1 from sorting_item_keyword existing
      where existing.item_id = 14
        and existing.language_code = k.language_code
        and existing.keyword = k.keyword
  );

delete from sorting_item_keyword where item_id = 4;
delete from sorting_item_translation where item_id = 4;
delete from sorting_item where id = 4;
