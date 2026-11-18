CREATE SEQUENCE lancamento_serie_id_seq AS INTEGER START WITH 1;

ALTER TABLE lancamento_financeiro
    ADD COLUMN serie_id INTEGER,
    ADD COLUMN numero_ocorrencia SMALLINT,
    ADD COLUMN total_ocorrencias SMALLINT,
    ADD CONSTRAINT ck_lancamento_serie_completa CHECK (
        (serie_id IS NULL AND numero_ocorrencia IS NULL AND total_ocorrencias IS NULL)
        OR (serie_id IS NOT NULL AND numero_ocorrencia IS NOT NULL
            AND total_ocorrencias IS NOT NULL AND serie_id > 0
            AND numero_ocorrencia >= 1
            AND total_ocorrencias >= 1
            AND numero_ocorrencia <= total_ocorrencias)
    ),
    ADD CONSTRAINT uq_lancamento_serie_ocorrencia
        UNIQUE (serie_id, numero_ocorrencia);

CREATE INDEX idx_lancamento_serie_owner
    ON lancamento_financeiro (owner_user_id, serie_id)
    WHERE serie_id IS NOT NULL;
