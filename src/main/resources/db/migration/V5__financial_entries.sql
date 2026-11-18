CREATE TABLE lancamento_financeiro (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id INTEGER NOT NULL REFERENCES usuario (id),
    categoria_id INTEGER NOT NULL,
    conta_financeira_id INTEGER NOT NULL,
    descricao VARCHAR(255) NOT NULL CHECK (length(btrim(descricao)) > 0),
    valor NUMERIC(19, 2) NOT NULL CHECK (valor > 0),
    mes_referencia DATE NOT NULL CHECK (EXTRACT(DAY FROM mes_referencia) = 1),
    data_prevista DATE,
    data_ocorrencia DATE,
    efetivado_em TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PREVISTO', 'EFETIVADO', 'CANCELADO')),
    criado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_lancamento_categoria_owner
        FOREIGN KEY (owner_user_id, categoria_id)
        REFERENCES categoria (owner_user_id, id),
    CONSTRAINT fk_lancamento_conta_owner
        FOREIGN KEY (owner_user_id, conta_financeira_id)
        REFERENCES conta_financeira (owner_user_id, id),
    CONSTRAINT uq_lancamento_owner_id UNIQUE (owner_user_id, id),
    CONSTRAINT ck_lancamento_estado_datas CHECK (
        (status = 'PREVISTO' AND data_prevista IS NOT NULL
            AND data_ocorrencia IS NULL AND efetivado_em IS NULL)
        OR (status = 'EFETIVADO' AND data_ocorrencia IS NOT NULL
            AND efetivado_em IS NOT NULL)
        OR (status = 'CANCELADO' AND data_ocorrencia IS NULL)
    )
);

CREATE INDEX idx_lancamento_owner_mes_status
    ON lancamento_financeiro (owner_user_id, mes_referencia, status);
CREATE INDEX idx_lancamento_conta_efetivado
    ON lancamento_financeiro (owner_user_id, conta_financeira_id)
    WHERE status = 'EFETIVADO';
