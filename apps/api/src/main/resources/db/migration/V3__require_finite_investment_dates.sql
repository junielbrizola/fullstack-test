ALTER TABLE investment
    ADD CONSTRAINT ck_investment_created_on_finite CHECK (isfinite(created_on)) NOT VALID;

ALTER TABLE withdrawal
    ADD CONSTRAINT ck_withdrawal_withdrawn_on_finite CHECK (isfinite(withdrawn_on)) NOT VALID;
