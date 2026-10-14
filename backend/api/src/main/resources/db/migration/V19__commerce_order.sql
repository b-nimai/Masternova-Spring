-- Commerce (Phase 9.3): orders and their lines. docs/lld/order-state-machine.md §3, §9.
-- `order` is an SQL keyword: the table is purchase_order.
CREATE TABLE purchase_order (
    id                  UUID         PRIMARY KEY,
    user_id             UUID         NOT NULL REFERENCES app_user (id),
    status              VARCHAR(10)  NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    subtotal_minor      BIGINT       NOT NULL,
    discount_minor      BIGINT       NOT NULL,
    total_minor         BIGINT       NOT NULL,
    coupon_code         VARCHAR(40)  REFERENCES coupon (code),
    provider            VARCHAR(20)  NOT NULL,          -- RAZORPAY · FAKE · NONE (a free order)
    provider_order_id   VARCHAR(100) UNIQUE,            -- set after the provider created it
    provider_payment_id VARCHAR(100),
    provider_refund_id  VARCHAR(100),
    last_payment_error  VARCHAR(500),
    created_at          TIMESTAMPTZ  NOT NULL,
    paid_at             TIMESTAMPTZ,
    refunded_at         TIMESTAMPTZ,
    version             BIGINT       NOT NULL,
    CONSTRAINT purchase_order_status_ck CHECK (status IN ('PENDING', 'FAILED', 'PAID', 'REFUNDED')),
    CONSTRAINT purchase_order_amounts_ck CHECK (
        subtotal_minor > 0 AND discount_minor >= 0 AND total_minor >= 0
        AND total_minor = subtotal_minor - discount_minor),
    -- ⭐ the state machine's footprints: a PAID order has its payment, a REFUNDED one its refund
    CONSTRAINT purchase_order_paid_ck     CHECK (status <> 'PAID' OR (paid_at IS NOT NULL AND provider_payment_id IS NOT NULL)),
    CONSTRAINT purchase_order_refunded_ck CHECK (status <> 'REFUNDED' OR refunded_at IS NOT NULL)
);

-- "my orders", newest first
CREATE INDEX purchase_order_user_idx ON purchase_order (user_id, created_at DESC);
-- the admin's list, newest first
CREATE INDEX purchase_order_created_idx ON purchase_order (created_at DESC);

CREATE TABLE order_line (
    order_id    UUID         NOT NULL REFERENCES purchase_order (id) ON DELETE CASCADE,
    course_id   UUID         NOT NULL REFERENCES course (id),
    slug        VARCHAR(140) NOT NULL,                 -- snapshot: what was sold, as sold
    title       VARCHAR(120) NOT NULL,
    price_minor BIGINT       NOT NULL CHECK (price_minor > 0),
    position    INT          NOT NULL,
    PRIMARY KEY (order_id, course_id)
);

CREATE INDEX order_line_course_idx ON order_line (course_id);
