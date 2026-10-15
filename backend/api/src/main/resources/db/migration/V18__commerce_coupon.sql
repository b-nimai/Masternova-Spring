-- Commerce (Phase 9.2): coupons. docs/lld/order-state-machine.md §3 (Pricing).
CREATE TABLE coupon (
    code               VARCHAR(40)  PRIMARY KEY,          -- stored upper-case: SAVE20, not save20
    kind               VARCHAR(10)  NOT NULL,
    percent_off        INT,                               -- PERCENT: 1..100
    amount_off_minor   BIGINT,                            -- AMOUNT: minor units
    max_discount_minor BIGINT,                            -- PERCENT only: the cap, if any
    currency           VARCHAR(3)   NOT NULL,
    min_subtotal_minor BIGINT       NOT NULL DEFAULT 0,
    course_id          UUID         REFERENCES course (id), -- NULL = the whole cart
    starts_at          TIMESTAMPTZ,
    ends_at            TIMESTAMPTZ,
    max_redemptions    INT,                               -- NULL = unlimited
    redemptions        INT          NOT NULL DEFAULT 0,   -- counted when an order is PAID
    active             BOOLEAN      NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL,
    CONSTRAINT coupon_code_ck CHECK (code ~ '^[A-Z0-9_-]{3,40}$'),
    -- ⭐ the kind decides which value column is set: the Strategy is chosen by data that can't
    --    be half one kind and half the other
    CONSTRAINT coupon_kind_ck CHECK (
        (kind = 'PERCENT' AND percent_off BETWEEN 1 AND 100 AND amount_off_minor IS NULL)
        OR (kind = 'AMOUNT' AND amount_off_minor > 0 AND percent_off IS NULL
            AND max_discount_minor IS NULL)),
    CONSTRAINT coupon_cap_ck         CHECK (max_discount_minor IS NULL OR max_discount_minor > 0),
    CONSTRAINT coupon_min_ck         CHECK (min_subtotal_minor >= 0),
    CONSTRAINT coupon_window_ck      CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT coupon_redemptions_ck CHECK (redemptions >= 0
        AND (max_redemptions IS NULL OR max_redemptions > 0))
);

CREATE INDEX coupon_course_idx ON coupon (course_id) WHERE course_id IS NOT NULL;
