-- What a published notice actually does to a resident.
--
-- Until now the notification consumer counted recipients and logged the
-- number, because this application has no push channel and inventing one to
-- justify the consumer would have been backwards. An in-app inbox needs no
-- external service, so the consumer can do its real job: turn one published
-- notice into one row per resident who asked to be told.
--
-- The unique key is the idempotency that matters. Kafka delivery is
-- at-least-once, so the consumer will sometimes see the same event twice;
-- without this, a redelivery would show residents the same notice again. The
-- previous in-memory deduplication reset on restart, which is exactly when a
-- consumer is most likely to replay.

create table resident_notification (
    id bigint primary key auto_increment,
    user_id bigint not null,
    notice_id bigint not null,
    event_id char(36) not null,
    created_at timestamp(3) not null default current_timestamp(3),
    read_at timestamp(3) null,

    foreign key (user_id) references app_user(id),
    unique key uq_resident_notification (user_id, event_id),
    -- The inbox query: one resident's unread notices, newest first.
    index idx_resident_notification_unread (user_id, read_at, created_at)
);
