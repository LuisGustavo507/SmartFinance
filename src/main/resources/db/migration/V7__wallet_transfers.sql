CREATE TABLE transferencia (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id INTEGER NOT NULL REFERENCES usuario (id),
    conta_origem_id INTEGER NOT NULL,
    conta_destino_id INTEGER NOT NULL,
    valor NUMERIC(19, 2) NOT NULL CHECK (valor > 0),
    descricao VARCHAR(255),
    transferida_em TIMESTAMPTZ NOT NULL,
    criada_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_transferencia_contas_diferentes
        CHECK (conta_origem_id <> conta_destino_id),
    CONSTRAINT fk_transferencia_origem_owner
        FOREIGN KEY (owner_user_id, conta_origem_id)
        REFERENCES conta_financeira (owner_user_id, id),
    CONSTRAINT fk_transferencia_destino_owner
        FOREIGN KEY (owner_user_id, conta_destino_id)
        REFERENCES conta_financeira (owner_user_id, id),
    CONSTRAINT uq_transferencia_owner_id UNIQUE (owner_user_id, id)
);

CREATE INDEX idx_transferencia_owner_data
    ON transferencia (owner_user_id, transferida_em);
