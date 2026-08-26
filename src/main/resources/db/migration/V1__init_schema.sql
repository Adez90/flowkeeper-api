-- Core schema: accounts (Personal or Organisation), the Department/Group
-- hierarchy beneath an Organisation, membership + role per account, the
-- configurable event-type taxonomy, and the Event log itself.
--
-- Deliberately not modelled here: password/credential storage (Keycloak
-- owns identity entirely — `users` only holds a profile keyed by the
-- Keycloak subject), and any peer-to-peer sharing/grant table (a later
-- phase — access today is fully expressed by account_members).

create extension if not exists pgcrypto;

create table accounts (
    id         uuid primary key default gen_random_uuid(),
    type       varchar(20)  not null check (type in ('PERSONAL', 'ORGANISATION')),
    name       varchar(200) not null,
    created_at timestamptz  not null default now()
);

create table departments (
    id         uuid primary key default gen_random_uuid(),
    account_id uuid         not null references accounts(id) on delete cascade,
    name       varchar(200) not null,
    created_at timestamptz  not null default now()
);
create index idx_departments_account on departments(account_id);

-- A Group sits under a Department, or directly under an Organisation
-- account when there's no department layer — this is how a single coach
-- (a small Organisation with one Group) is represented without forcing
-- an empty Department in between.
create table groups (
    id            uuid primary key default gen_random_uuid(),
    account_id    uuid         not null references accounts(id) on delete cascade,
    department_id uuid         references departments(id) on delete cascade,
    name          varchar(200) not null,
    created_at    timestamptz  not null default now()
);
create index idx_groups_account on groups(account_id);
create index idx_groups_department on groups(department_id);

-- Identity lives in Keycloak; this is the local profile row a user's
-- token subject resolves to.
create table users (
    id                uuid primary key default gen_random_uuid(),
    keycloak_subject  varchar(64)  not null unique,
    display_name      varchar(200) not null,
    email             varchar(320) not null,
    created_at        timestamptz  not null default now()
);
create unique index idx_users_email on users(lower(email));

-- Which account(s) a user belongs to, their role within it, and — for
-- Organisation accounts — which Department/Group scopes that to. A user
-- always has at least one row here for their own Personal account.
create table account_members (
    id            uuid primary key default gen_random_uuid(),
    account_id    uuid        not null references accounts(id) on delete cascade,
    user_id       uuid        not null references users(id) on delete cascade,
    department_id uuid        references departments(id) on delete set null,
    group_id      uuid        references groups(id) on delete set null,
    role          varchar(20) not null check (role in ('OWNER', 'ADMIN', 'COACH', 'MEMBER')),
    created_at    timestamptz not null default now(),
    unique (account_id, user_id)
);
create index idx_account_members_user on account_members(user_id);
create index idx_account_members_account on account_members(account_id);

-- account_id null = a global default type seeded below, available to
-- every account. Non-null = a type a specific account defined for itself.
create table event_types (
    id         uuid primary key default gen_random_uuid(),
    account_id uuid         references accounts(id) on delete cascade,
    code       varchar(50)  not null,
    label      varchar(100) not null,
    icon       varchar(50),
    is_default boolean      not null default false,
    created_at timestamptz  not null default now(),
    unique (account_id, code)
);
create index idx_event_types_account on event_types(account_id);

create table events (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid        not null references users(id) on delete cascade,
    account_id      uuid        not null references accounts(id) on delete cascade,
    event_type_id   uuid        not null references event_types(id),
    status          varchar(20) not null default 'OPEN' check (status in ('OPEN', 'COMPLETED')),
    ingoing_energy  smallint    not null check (ingoing_energy between 1 and 5),
    ingoing_note    text,
    outgoing_energy smallint    check (outgoing_energy between 1 and 5),
    outgoing_note   text,
    started_at      timestamptz not null default now(),
    completed_at    timestamptz,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);
-- Both indexes support the day/week/month rollup queries: per-user for a
-- Personal dashboard, per-account for an Organisation/coach view.
create index idx_events_user_started on events(user_id, started_at);
create index idx_events_account_started on events(account_id, started_at);

-- Baseline event types every new account starts with, reusing the
-- category set already designed (and iconed) for FlowKeeperPortal.
insert into event_types (account_id, code, label, icon, is_default) values
    (null, 'meeting',       'Meeting',            'meeting',       true),
    (null, 'phone',         'Phone call',         'phone',         true),
    (null, 'errand',        'Errand',             'errand',        true),
    (null, 'physical',      'Physical activity',  'physical',      true),
    (null, 'relaxation',    'Relaxation',         'relaxation',    true),
    (null, 'digital',       'Digital',            'digital',       true),
    (null, 'health',        'Health',             'health',        true),
    (null, 'concentration', 'Concentration',      'concentration', true);
