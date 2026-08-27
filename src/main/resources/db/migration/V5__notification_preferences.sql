-- Self-reminder nudges: which channel(s) a user has opted into, and where
-- to reach them on each. All default false/null — nothing is sent unless
-- the user explicitly turns a channel on. See the Blueprint for the full
-- design.

alter table users add column notify_in_app boolean not null default false;
alter table users add column notify_push boolean not null default false;
alter table users add column notify_email boolean not null default false;
alter table users add column expo_push_token varchar(200);

comment on column users.notify_in_app is 'Opted in to seeing reminder nudges (unfinished event, unused account) in-app.';
comment on column users.notify_push is 'Opted in to the same nudges as a mobile push notification. No-op until an expo_push_token is also on file.';
comment on column users.notify_email is 'Opted in to the same nudges by email.';
comment on column users.expo_push_token is 'The device token Expo''s push service delivers to — registered by the mobile client, null until then.';

-- The in-app delivery channel itself: a small inbox each user's client
-- polls/fetches. Not a general-purpose events table — just what
-- notify_in_app populates.
create table in_app_notifications (
    id uuid primary key,
    user_id uuid not null references users(id),
    type varchar(50) not null,
    message varchar(500) not null,
    created_at timestamptz not null,
    read_at timestamptz
);

create index idx_in_app_notifications_user on in_app_notifications(user_id, created_at desc);
