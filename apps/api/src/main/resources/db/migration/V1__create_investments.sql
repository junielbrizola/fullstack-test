CREATE TABLE investment (
    id UUID PRIMARY KEY,
    owner_name VARCHAR(100) NOT NULL,
    created_on DATE NOT NULL,
    initial_amount NUMERIC(19, 2) NOT NULL,
    creation_idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_investment_owner_name_nonblank CHECK (length(btrim(owner_name)) > 0),
    CONSTRAINT ck_investment_creation_key_length CHECK (
        length(creation_idempotency_key) BETWEEN 8 AND 100 AND length(btrim(creation_idempotency_key)) > 0
    ),
    CONSTRAINT ck_investment_initial_amount_nonnegative CHECK (initial_amount >= 0)
);

CREATE TABLE withdrawal (
    id UUID PRIMARY KEY,
    investment_id UUID NOT NULL UNIQUE REFERENCES investment (id),
    withdrawn_on DATE NOT NULL,
    initial_amount NUMERIC(19, 2) NOT NULL,
    completed_periods BIGINT NOT NULL,
    gross_amount NUMERIC NOT NULL,
    gain_amount NUMERIC NOT NULL,
    tax_rate NUMERIC(5, 4) NOT NULL,
    tax_amount NUMERIC NOT NULL,
    net_amount NUMERIC NOT NULL,
    calculation_version VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_withdrawal_amounts_nonnegative CHECK (
        initial_amount >= 0 AND completed_periods >= 0 AND gross_amount >= 0
        AND gain_amount >= 0 AND tax_rate >= 0 AND tax_rate <= 1
        AND tax_amount >= 0 AND net_amount >= 0
    ),
    CONSTRAINT ck_withdrawal_tax_rate_allowed CHECK (tax_rate IN (0.2250, 0.1850, 0.1500)),
    CONSTRAINT ck_withdrawal_amounts_cents CHECK (
        gross_amount = round(gross_amount, 2) AND gain_amount = round(gain_amount, 2)
        AND tax_amount = round(tax_amount, 2) AND net_amount = round(net_amount, 2)
    ),
    CONSTRAINT ck_withdrawal_gross_matches_components CHECK (gross_amount = initial_amount + gain_amount),
    CONSTRAINT ck_withdrawal_net_matches_components CHECK (net_amount = gross_amount - tax_amount),
    CONSTRAINT ck_withdrawal_tax_within_gain CHECK (tax_amount <= gain_amount),
    CONSTRAINT ck_withdrawal_tax_matches_gain CHECK (tax_amount = round(gain_amount * tax_rate, 2)),
    CONSTRAINT ck_withdrawal_version_nonblank CHECK (length(btrim(calculation_version)) > 0),
    CONSTRAINT ck_withdrawal_key_length CHECK (
        length(idempotency_key) BETWEEN 8 AND 100 AND length(btrim(idempotency_key)) > 0
    )
);

CREATE INDEX idx_investment_owner_created_id
    ON investment (owner_name, created_on DESC, id DESC);

CREATE INDEX idx_investment_created_id
    ON investment (created_on DESC, id DESC);

CREATE FUNCTION enforce_withdrawal_investment_rules()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    investment_created_on DATE;
    investment_initial_amount NUMERIC(19, 2);
    expected_tax_rate NUMERIC(5, 4);
BEGIN
    SELECT created_on, initial_amount
      INTO investment_created_on, investment_initial_amount
      FROM investment
     WHERE id = NEW.investment_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'investment does not exist'
            USING ERRCODE = '23503';
    END IF;

    IF NEW.initial_amount <> investment_initial_amount THEN
        RAISE EXCEPTION 'withdrawal principal must match the investment principal'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.withdrawn_on < investment_created_on THEN
        RAISE EXCEPTION 'withdrawal date cannot precede investment creation'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.withdrawn_on < (investment_created_on + INTERVAL '1 year')::DATE THEN
        expected_tax_rate := 0.2250;
    ELSIF NEW.withdrawn_on <= (investment_created_on + INTERVAL '2 years')::DATE THEN
        expected_tax_rate := 0.1850;
    ELSE
        expected_tax_rate := 0.1500;
    END IF;

    IF NEW.tax_rate <> expected_tax_rate THEN
        RAISE EXCEPTION 'withdrawal tax rate does not match investment age'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_withdrawal_investment_rules
    BEFORE INSERT OR UPDATE ON withdrawal
    FOR EACH ROW
    EXECUTE FUNCTION enforce_withdrawal_investment_rules();
