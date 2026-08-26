-- Basic profile beyond identity: timezone (so statistics can eventually
-- mean the user's own "day", not a UTC day), locale, and an avatar. Avatar
-- is a URL only for now — no file upload/storage subsystem exists yet,
-- that's a separate decision (local disk on the VPS vs. object storage).

alter table users
    add column timezone   varchar(50)  not null default 'UTC',
    add column locale     varchar(10),
    add column avatar_url varchar(500),
    add column updated_at timestamptz  not null default now();
