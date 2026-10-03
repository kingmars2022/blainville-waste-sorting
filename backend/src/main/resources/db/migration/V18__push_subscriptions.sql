-- Browser push subscriptions, so a notice can reach a resident who is not on
-- the site.
--
-- The resident inbox already exists and stays the system of record: a push is
-- a second delivery of something that is in the inbox either way. That order
-- matters - a push that fails, or a browser that has revoked permission,
-- must not be able to lose a notice.
--
-- A subscription is three opaque values the browser hands over: the endpoint
-- its push service listens on, and the two keys (p256dh, auth) the message is
-- encrypted to. The server cannot create them and cannot renew them; when one
-- expires the push service answers 404 or 410 and the row is deleted.

create table push_subscription (
    id bigint primary key auto_increment,
    user_id bigint not null,
    -- Push services hand out long URLs. 500 is comfortably above what FCM and
    -- Mozilla autopush actually emit, and the unique key needs a bounded
    -- column anyway.
    endpoint varchar(500) not null,
    -- The subscription's public key, an uncompressed P-256 point, and the
    -- 16-byte auth secret. Stored base64url exactly as the browser gave them.
    p256dh varchar(255) not null,
    auth varchar(64) not null,
    -- Which language to send this device, captured when it subscribed. The
    -- alternative is reading the owner's preference at send time, which is a
    -- query per device for a value that barely changes.
    language_code varchar(10) not null default 'fr',
    created_at timestamp not null default current_timestamp,
    -- The endpoint identifies the device, not the user: the same person on a
    -- phone and a laptop has two, and re-subscribing on one device returns the
    -- same endpoint and must update the row rather than add another.
    unique key uq_push_subscription_endpoint (endpoint),
    key idx_push_subscription_user (user_id),
    constraint fk_push_subscription_user foreign key (user_id)
        references app_user (id) on delete cascade
);
