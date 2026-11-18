-- Existing expenses remain unclassified; never infer a user's past intent.
ALTER TABLE lancamento_financeiro
    ADD COLUMN necessidade VARCHAR(20)
        CHECK (necessidade IN ('NECESSARIA', 'NEUTRA', 'FUTIL'));
