-- Promo/trial codes: an admin (see app.admin.emails) generates a code
-- granting N days of full access, redeemable by an account's OWNER.
-- Single-use for an individual ("try it free for 3 months") or capped at
-- N for a company by setting max_redemptions accordingly. Redeeming
-- creates or extends the account's subscriptions row — same table real
-- Stripe-driven subscriptions live in — so a promo grant isn't tied to
-- any specific paid price.

alter table subscriptions alter column price_id drop not null;

create table promo_codes (
    id                uuid primary key default gen_random_uuid(),
    code              varchar(32)  not null unique,
    duration_days     integer      not null check (duration_days > 0),
    max_redemptions   integer      not null default 1 check (max_redemptions > 0),
    redemption_count  integer      not null default 0,
    -- Null = no redeem-by deadline, just capped by max_redemptions.
    expires_at        timestamptz,
    -- The admin's own label for what this code was for, e.g. "Acme AB pilot".
    note              varchar(500),
    created_by_email  varchar(320) not null,
    created_at        timestamptz  not null default now(),
    -- Null = active; set to invalidate a code early, independent of expires_at/max_redemptions.
    revoked_at        timestamptz
);

create table promo_code_redemptions (
    id             uuid primary key default gen_random_uuid(),
    promo_code_id  uuid        not null references promo_codes(id) on delete cascade,
    account_id     uuid        not null references accounts(id) on delete cascade,
    redeemed_by    uuid        not null references users(id),
    redeemed_at    timestamptz not null default now(),
    -- Same account can't redeem the same code twice — extending an
    -- existing grant is done by redeeming a different code, not the same one again.
    unique (promo_code_id, account_id)
);
create index idx_promo_code_redemptions_account on promo_code_redemptions(account_id);
