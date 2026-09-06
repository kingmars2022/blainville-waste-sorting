create table app_user (
    id bigint primary key auto_increment,
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    role varchar(20) not null default 'USER',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp
);

create table user_preference (
    user_id bigint primary key,
    sector varchar(20) not null,
    language_code varchar(10) not null default 'fr',
    reminder_enabled boolean not null default true,
    reminder_time time not null default '20:00:00',
    foreign key (user_id) references app_user(id)
);

create table collection_event (
    id bigint primary key auto_increment,
    collection_date date not null,
    sector varchar(20) not null,
    collection_type varchar(40) not null,
    bin_color varchar(20) not null,
    note_fr text,
    note_en text,
    note_zh text,
    source_url varchar(1000),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    index idx_collection_event_date_sector (collection_date, sector)
);

create table sorting_item (
    id bigint primary key auto_increment,
    destination_type varchar(40) not null,
    bin_color varchar(20) not null,
    source_url varchar(1000),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp
);

create table sorting_item_translation (
    id bigint primary key auto_increment,
    item_id bigint not null,
    language_code varchar(10) not null,
    name varchar(255) not null,
    instruction text not null,
    foreign key (item_id) references sorting_item(id),
    unique key uq_sorting_item_translation (item_id, language_code)
);

create table sorting_item_keyword (
    id bigint primary key auto_increment,
    item_id bigint not null,
    language_code varchar(10) not null,
    keyword varchar(255) not null,
    foreign key (item_id) references sorting_item(id),
    index idx_sorting_item_keyword (language_code, keyword)
);

create table special_notice (
    id bigint primary key auto_increment,
    starts_on date not null,
    ends_on date not null,
    title_fr varchar(255) not null,
    title_en varchar(255) not null,
    title_zh varchar(255) not null,
    body_fr text not null,
    body_en text not null,
    body_zh text not null,
    source_url varchar(1000),
    active boolean not null default true
);

