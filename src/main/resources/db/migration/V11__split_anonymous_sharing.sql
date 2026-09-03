-- Splits the single opt-in "share_anonymously" flag into two independent
-- ones, one per note — an activity's pre-note and post-note are often
-- written in very different moods, and an owner may want to share only
-- one of them rather than both together.

alter table events add column share_ingoing_note_anonymously boolean not null default false;
alter table events add column share_outgoing_note_anonymously boolean not null default false;

comment on column events.share_ingoing_note_anonymously is
    'Whether this event''s pre-activity (ingoing) note may be surfaced, stripped of author identity, as organisation-wide anonymous feedback. Set only by the event''s own owner.';
comment on column events.share_outgoing_note_anonymously is
    'Whether this event''s post-activity (outgoing) note may be surfaced, stripped of author identity, as organisation-wide anonymous feedback. Set only by the event''s own owner.';

-- Preserve existing opt-ins: anyone who'd already opted in under the old
-- all-or-nothing flag keeps sharing both notes.
update events set share_ingoing_note_anonymously = share_anonymously, share_outgoing_note_anonymously = share_anonymously
    where share_anonymously = true;

alter table events drop column share_anonymously;
