CREATE TABLE credito_estorno (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id INTEGER NOT NULL REFERENCES usuario(id),
    compra_cartao_id INTEGER NOT NULL UNIQUE,
    valor_original NUMERIC(19, 2) NOT NULL CHECK (valor_original > 0),
    criado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_credito_estorno_owner_id UNIQUE (owner_user_id, id),
    CONSTRAINT fk_credito_estorno_compra FOREIGN KEY (owner_user_id, compra_cartao_id)
        REFERENCES compra_cartao (owner_user_id, id)
);

CREATE TABLE aplicacao_credito (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id INTEGER NOT NULL REFERENCES usuario(id),
    credito_estorno_id INTEGER NOT NULL,
    fatura_cartao_id INTEGER NOT NULL,
    valor NUMERIC(19, 2) NOT NULL CHECK (valor > 0),
    criada_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_aplicacao_credito_owner_id UNIQUE (owner_user_id, id),
    CONSTRAINT uq_aplicacao_credito_destino UNIQUE (credito_estorno_id, fatura_cartao_id),
    CONSTRAINT fk_aplicacao_credito_origem FOREIGN KEY (owner_user_id, credito_estorno_id)
        REFERENCES credito_estorno (owner_user_id, id),
    CONSTRAINT fk_aplicacao_credito_fatura FOREIGN KEY (owner_user_id, fatura_cartao_id)
        REFERENCES fatura_cartao (owner_user_id, id)
);

CREATE INDEX idx_credito_estorno_owner_created
    ON credito_estorno (owner_user_id, criado_em, id);
CREATE INDEX idx_aplicacao_credito_fatura
    ON aplicacao_credito (fatura_cartao_id);
