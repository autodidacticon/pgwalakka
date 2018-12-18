create schema if not exists walakka;

create table walakka.slot_catchup(
    slot_name text,
    catchup_lsn text
)