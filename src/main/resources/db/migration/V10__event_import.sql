-- Loosens ingoing_energy so an imported (but not yet started) activity can
-- exist as a real, listed event before the user has actually rated it —
-- the "tap to start" state the manual create-event flow never needed,
-- since that one always collects ingoing energy up front.
alter table events alter column ingoing_energy drop not null;

-- Ties an imported event back to the calendar/Strava item it came from,
-- and is what prevents importing the same item twice: pressing "Import
-- events" again later in the day must skip anything already brought in.
-- A NULL in a unique index never collides with anything, including
-- another NULL, so this only constrains actual imports — manually
-- logged events (both columns left null) are unaffected.
alter table events add column external_provider varchar(30)
    check (external_provider in ('GOOGLE_CALENDAR', 'MICROSOFT_CALENDAR', 'APPLE_CALENDAR', 'STRAVA'));
alter table events add column external_id varchar(255);
create unique index idx_events_external_dedupe on events(user_id, external_provider, external_id);

-- The provider's own end time, offered as the default when finalizing an
-- imported event since the calendar/Strava already knows it — never used
-- to set outgoing_energy or outgoing_note on its own, those still need
-- the user's own input.
alter table events add column external_ended_at timestamptz;
