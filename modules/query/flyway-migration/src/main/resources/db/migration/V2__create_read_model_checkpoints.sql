create table "read_model_checkpoints"
(
    "persistence_id" varchar(255)             not null primary key,
    "sequence_nr"    bigint                   not null,
    "updated_at"     timestamp with time zone not null default current_timestamp
);
