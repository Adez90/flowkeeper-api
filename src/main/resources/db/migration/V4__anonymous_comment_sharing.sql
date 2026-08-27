-- Opt-in, per-event anonymous sharing of an event's notes as organisation
-- feedback ("what's working, what's not"). Defaults false: a note is
-- private until its own author explicitly opts it in. See the Blueprint
-- for the full design.

alter table events add column share_anonymously boolean not null default false;
comment on column events.share_anonymously is
    'Whether this event''s notes may be surfaced, stripped of author identity, as organisation-wide anonymous feedback. Set only by the event''s own owner.';
