-- Per-level consent to share a Flow % aggregate laterally with peers at
-- the same level of the org ladder (member -> group -> department -> org).
-- The level above always sees its direct children's aggregate regardless
-- of this flag (that's a supervisory view, not peer sharing) — this only
-- controls visibility to peers at the same level. See the Blueprint for
-- the full design.

alter table account_members add column share_flow_with_peers boolean not null default false;
comment on column account_members.share_flow_with_peers is
    'Whether this member''s personal Flow % is visible to fellow members of the same group.';

alter table groups add column share_flow_with_peers boolean not null default false;
comment on column groups.share_flow_with_peers is
    'Whether this group''s aggregate Flow % is visible to fellow groups in the same department (set by the group''s manager).';

alter table departments add column share_flow_with_peers boolean not null default false;
comment on column departments.share_flow_with_peers is
    'Whether this department''s aggregate Flow % is visible to fellow departments in the org (set by the department''s admin).';
