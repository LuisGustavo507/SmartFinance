ALTER TABLE lancamento_financeiro DROP CONSTRAINT ck_lancamento_estado_datas;

ALTER TABLE lancamento_financeiro ADD CONSTRAINT ck_lancamento_estado_datas CHECK (
    (status = 'PREVISTO' AND data_prevista IS NOT NULL
        AND data_ocorrencia IS NULL AND efetivado_em IS NULL)
    OR (status = 'EFETIVADO' AND data_ocorrencia IS NOT NULL
        AND efetivado_em IS NOT NULL)
    OR (status = 'CANCELADO' AND (
        (data_ocorrencia IS NULL AND efetivado_em IS NULL)
        OR (data_ocorrencia IS NOT NULL AND efetivado_em IS NOT NULL)
    ))
);
