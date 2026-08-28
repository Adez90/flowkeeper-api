-- Subscription billing: a Plan (Personal or Business) offers multiple
-- Prices (one per period/billing-type combination); an Account has at
-- most one current Subscription. Real payment processing goes through a
-- PaymentGateway (Stripe, once configured) — this schema tracks the
-- resulting state, not the checkout flow itself.
--
-- Price amounts seeded below are PLACEHOLDERS in SEK ören, not real
-- pricing — update them once actual amounts are decided, before this
-- ever goes live.

create table plans (
    id         uuid primary key default gen_random_uuid(),
    code       varchar(50)  not null unique,
    scope      varchar(20)  not null check (scope in ('PERSONAL', 'BUSINESS')),
    name       varchar(100) not null,
    created_at timestamptz  not null default now()
);

-- period is the subscription length, independent of scope — "yearly"
-- (business) and "12 months" (personal) are the same period, so there's
-- no separate QUARTER/YEAR concept, just the shared duration set.
create table prices (
    id                 uuid primary key default gen_random_uuid(),
    plan_id            uuid        not null references plans(id) on delete cascade,
    period             varchar(20) not null check (period in
        ('ONE_MONTH', 'THREE_MONTHS', 'SIX_MONTHS', 'TWELVE_MONTHS', 'TWO_YEARS', 'THREE_YEARS', 'FOUR_YEARS', 'FIVE_YEARS')),
    billing_type       varchar(20) not null check (billing_type in ('ONE_TIME', 'RECURRING')),
    -- true = amount_minor_units is a PER-SEAT price, multiplied by seat
    -- count at checkout; false = a flat price for the whole account
    -- (always false for the Personal plan).
    per_seat           boolean     not null default false,
    amount_minor_units bigint      not null,
    currency           varchar(3)  not null,
    active             boolean     not null default true,
    created_at         timestamptz not null default now(),
    unique (plan_id, period, billing_type)
);
create index idx_prices_plan on prices(plan_id);

-- One row per account tracking its current plan — history of past
-- subscriptions/payments lives in payment_events, not here.
create table subscriptions (
    id                        uuid primary key default gen_random_uuid(),
    account_id                uuid        not null references accounts(id) on delete cascade unique,
    price_id                  uuid        not null references prices(id),
    seat_count                integer,
    status                    varchar(20) not null check (status in
        ('INCOMPLETE', 'ACTIVE', 'PAST_DUE', 'CANCELED', 'EXPIRED')),
    current_period_end        timestamptz,
    provider                  varchar(20) not null default 'STRIPE',
    provider_customer_id      varchar(255),
    provider_subscription_id  varchar(255),
    created_at                timestamptz not null default now(),
    updated_at                timestamptz not null default now()
);
create index idx_subscriptions_account on subscriptions(account_id);

-- Raw webhook deliveries from the payment provider, kept for audit and to
-- make webhook processing idempotent (a provider may redeliver the same
-- event more than once).
create table payment_events (
    id                 uuid primary key default gen_random_uuid(),
    account_id         uuid references accounts(id) on delete set null,
    provider           varchar(20)  not null default 'STRIPE',
    provider_event_id  varchar(255) not null,
    type               varchar(100) not null,
    payload            text         not null,
    received_at        timestamptz  not null default now(),
    unique (provider, provider_event_id)
);

insert into plans (code, scope, name) values
    ('personal', 'PERSONAL', 'Personal'),
    ('business', 'BUSINESS', 'Business');

-- Personal: every period as both one-time and recurring, flat (not per-seat).
insert into prices (plan_id, period, billing_type, per_seat, amount_minor_units, currency)
select plans.id, p.period, p.billing_type, false, p.amount, 'SEK'
from plans, (values
    ('ONE_MONTH',     'ONE_TIME',   9900),
    ('ONE_MONTH',     'RECURRING',  9900),
    ('THREE_MONTHS',  'ONE_TIME',   27900),
    ('THREE_MONTHS',  'RECURRING',  27900),
    ('SIX_MONTHS',    'ONE_TIME',   53900),
    ('SIX_MONTHS',    'RECURRING',  53900),
    ('TWELVE_MONTHS', 'ONE_TIME',   99900),
    ('TWELVE_MONTHS', 'RECURRING',  99900)
) as p(period, billing_type, amount)
where plans.code = 'personal';

-- Business: quarterly/yearly as both one-time and recurring, per-seat;
-- multi-year (2-5 years) as one-time prepay only, per-seat.
insert into prices (plan_id, period, billing_type, per_seat, amount_minor_units, currency)
select plans.id, p.period, p.billing_type, true, p.amount, 'SEK'
from plans, (values
    ('THREE_MONTHS',  'ONE_TIME',   19900),
    ('THREE_MONTHS',  'RECURRING',  19900),
    ('TWELVE_MONTHS', 'ONE_TIME',   69900),
    ('TWELVE_MONTHS', 'RECURRING',  69900),
    ('TWO_YEARS',     'ONE_TIME',   129900),
    ('THREE_YEARS',   'ONE_TIME',   179900),
    ('FOUR_YEARS',    'ONE_TIME',   219900),
    ('FIVE_YEARS',    'ONE_TIME',   249900)
) as p(period, billing_type, amount)
where plans.code = 'business';
