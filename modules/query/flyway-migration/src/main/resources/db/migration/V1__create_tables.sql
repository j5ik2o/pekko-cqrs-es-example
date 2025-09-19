create table "user_accounts"
(
    "id"            char(26)                  not null primary key,
    "first_name"    varchar                   not null,
    "last_name"     varchar                   not null,
    "created_at"    timestamp with time zone  not null default current_timestamp,
    "updated_at"    timestamp with time zone  not null default current_timestamp
);
