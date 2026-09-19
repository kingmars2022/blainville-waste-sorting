-- Transactional outbox for notice events.
--
-- The naive version of "publish an event when a notice changes" is to call the
-- broker from the service method. That is a distributed write with no
-- transaction around it: the database commit and the broker publish can each
-- succeed while the other fails, so residents either get told about a notice
-- that was rolled back, or never told about one that exists. No amount of
-- retrying fixes it, because the failure is in the gap between two systems.
--
-- This table closes the gap. The event row is inserted in the SAME MySQL
-- transaction as the business write, so it commits if and only if the notice
-- does. A relay then moves committed rows to Kafka afterwards, at-least-once.
-- Consumers must therefore be idempotent - which is the trade this pattern
-- makes: exactly-once delivery is replaced by exactly-once *effect*, enforced
-- on the consumer side by the event id.

create table outbox_event (
    id bigint primary key auto_increment,
    event_id char(36) not null,
    aggregate_type varchar(40) not null,
    aggregate_id bigint not null,
    event_type varchar(60) not null,
    payload json not null,
    occurred_at timestamp(3) not null default current_timestamp(3),
    published_at timestamp(3) null,

    -- The relay's only query: unpublished rows, oldest first.
    index idx_outbox_unpublished (published_at, id),
    -- Consumers dedupe on this, so it has to be unique at the source too.
    unique key uq_outbox_event_id (event_id)
);

-- The audit trail's relational home.
--
-- A deliberate control for the MongoDB implementation of the same interface:
-- `before_state` and `after_state` are MySQL's native JSON type, which holds
-- the differently-shaped entity snapshots without a column per field. Keeping
-- both makes "why a document store?" a question this project can answer by
-- comparison instead of assertion.

create table audit_record (
    id bigint primary key auto_increment,
    -- Unique, because delivery is at-least-once and a replayed event must not
    -- produce a second row. The insert relies on this key, not on a prior read.
    event_id char(36) not null,
    entity_type varchar(40) not null,
    entity_id bigint not null,
    action varchar(20) not null,
    actor varchar(255),
    occurred_at timestamp(3) not null,
    before_state json,
    after_state json,

    unique key uq_audit_event_id (event_id),
    index idx_audit_entity (entity_type, entity_id, occurred_at)
);
