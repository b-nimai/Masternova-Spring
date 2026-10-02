-- Catalog prices in ONE currency (found in the Phase 5 review). The price sorts and the free/paid
-- filter compare price_minor in SQL; paise and cents aren't comparable, so a mixed catalog sorted
-- a $19.99 course below a ₹499 one. Course.CATALOG_CURRENCY enforces it in the aggregate; this
-- constraint enforces it for everything else. (V6's constraint allowed INR or USD.)
ALTER TABLE course DROP CONSTRAINT course_currency_ck;
ALTER TABLE course ADD CONSTRAINT course_currency_ck CHECK (currency = 'INR');
