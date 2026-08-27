-- Coach-to-member 1:1 feedback: a supervisor's note about one specific
-- member, either attached to one of that member's own events or freeform
-- (a periodic check-in note not tied to any single activity). One
-- direction only — the coach/supervisor writes it, the member (and anyone
-- who supervises them) reads it. See the Blueprint for the full design.

create table coach_feedback (
    id uuid primary key,
    account_id uuid not null references accounts(id),
    coach_id uuid not null references users(id),
    member_id uuid not null references users(id),
    event_id uuid references events(id),
    note varchar(2000) not null,
    created_at timestamptz not null
);

create index idx_coach_feedback_member on coach_feedback (account_id, member_id, created_at desc);
