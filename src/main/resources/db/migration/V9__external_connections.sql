-- External calendar/fitness sync: a user connects one provider account
-- per (account, provider) via OAuth (Google/Microsoft/Strava — Apple's
-- path is different, see the Blueprint, and isn't wired up yet). This
-- migration only tracks the connection itself; actually pulling events
-- from a connected provider into the events table is a later phase.
--
-- access_token/refresh_token are plain text for now — nothing here goes
-- live until real OAuth credentials exist, and encrypting these at rest
-- is a call to make before that happens, not before this schema exists.

create table external_connections (
    id                        uuid primary key default gen_random_uuid(),
    user_id                   uuid        not null references users(id) on delete cascade,
    account_id                uuid        not null references accounts(id) on delete cascade,
    provider                  varchar(30) not null check (provider in
        ('GOOGLE_CALENDAR', 'MICROSOFT_CALENDAR', 'APPLE_CALENDAR', 'STRAVA')),
    status                    varchar(20) not null check (status in ('CONNECTED', 'ERROR', 'DISCONNECTED')),
    -- The connected provider account's own label (email, athlete name, ...) — display only.
    external_account_label    varchar(320),
    access_token              text,
    refresh_token             text,
    token_expires_at          timestamptz,
    last_synced_at            timestamptz,
    last_error                text,
    created_at                timestamptz not null default now(),
    updated_at                timestamptz not null default now(),
    unique (user_id, account_id, provider)
);
create index idx_external_connections_account on external_connections(account_id);

-- Short-lived, single-use — issued when a user starts an OAuth
-- authorization and consumed by the callback, so the callback (which
-- carries no bearer token, just whatever the provider redirects back)
-- can be tied back to the right user/account without trusting anything
-- the browser or provider sends except this opaque value.
create table oauth_states (
    state         varchar(64) primary key,
    user_id       uuid        not null references users(id) on delete cascade,
    account_id    uuid        not null references accounts(id) on delete cascade,
    provider      varchar(30) not null,
    redirect_uri  text        not null,
    created_at    timestamptz not null default now(),
    expires_at    timestamptz not null
);
