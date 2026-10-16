ALTER TABLE investment
    VALIDATE CONSTRAINT ck_investment_created_on_finite;

ALTER TABLE withdrawal
    VALIDATE CONSTRAINT ck_withdrawal_withdrawn_on_finite;
