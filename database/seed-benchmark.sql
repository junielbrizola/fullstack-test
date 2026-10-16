-- Dados reproduzíveis e sintéticos para medir a listagem local.
-- Reexecutar não duplica linhas; não use este fixture fora do banco local.
BEGIN;

INSERT INTO investment (id, owner_name, created_on, initial_amount, creation_idempotency_key)
SELECT gen_random_uuid(),
       'BENCHMARK-LOCAL-G' || lpad((((sequence_number.value - 1) / 1000) + 1)::TEXT, 2, '0'),
       DATE '2020-01-01' + ((sequence_number.value - 1) % 1000),
       (1000 + ((sequence_number.value - 1) % 1000))::NUMERIC(19, 2),
       'benchmark-local-' || lpad(sequence_number.value::TEXT, 5, '0')
  FROM generate_series(1, 10000) AS sequence_number(value)
ON CONFLICT (creation_idempotency_key) DO NOTHING;

DO $$
BEGIN
    IF (SELECT count(*)
          FROM investment
         WHERE owner_name LIKE 'BENCHMARK-LOCAL-G%'
           AND creation_idempotency_key LIKE 'benchmark-local-%') <> 10000 THEN
        RAISE EXCEPTION 'The local benchmark dataset must contain exactly 10000 investments';
    END IF;

    IF (SELECT count(DISTINCT owner_name)
          FROM investment
         WHERE owner_name LIKE 'BENCHMARK-LOCAL-G%'
           AND creation_idempotency_key LIKE 'benchmark-local-%') <> 10
       OR EXISTS (
            SELECT 1
              FROM investment
             WHERE owner_name LIKE 'BENCHMARK-LOCAL-G%'
               AND creation_idempotency_key LIKE 'benchmark-local-%'
             GROUP BY owner_name
            HAVING count(*) <> 1000
       ) THEN
        RAISE EXCEPTION 'The local benchmark dataset must have ten owners with 1000 investments each';
    END IF;
END;
$$;

ANALYZE investment;
COMMIT;
