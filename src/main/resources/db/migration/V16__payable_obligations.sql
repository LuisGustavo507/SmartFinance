CREATE TABLE conta_pagar (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id INTEGER NOT NULL REFERENCES usuario (id),
    categoria_id INTEGER NOT NULL,
    entidade_id INTEGER,
    descricao VARCHAR(255) NOT NULL DEFAULT '',
    necessidade VARCHAR(20) NOT NULL CHECK (necessidade IN ('NECESSARIA', 'NEUTRA', 'FUTIL')),
    valor NUMERIC(19, 2) NOT NULL CHECK (valor > 0),
    data_vencimento DATE NOT NULL,
    cancelada_em TIMESTAMPTZ,
    criada_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_conta_pagar_owner_id UNIQUE (owner_user_id, id),
    CONSTRAINT fk_conta_pagar_categoria FOREIGN KEY (owner_user_id, categoria_id)
        REFERENCES categoria (owner_user_id, id),
    CONSTRAINT fk_conta_pagar_entidade FOREIGN KEY (owner_user_id, entidade_id)
        REFERENCES entidade (owner_user_id, id)
);

CREATE INDEX idx_conta_pagar_owner_vencimento
    ON conta_pagar (owner_user_id, data_vencimento);

CREATE TABLE pagamento_conta_pagar (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id INTEGER NOT NULL REFERENCES usuario (id),
    conta_pagar_id INTEGER NOT NULL,
    conta_financeira_id INTEGER NOT NULL,
    valor NUMERIC(19, 2) NOT NULL CHECK (valor > 0),
    pago_em DATE NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    estornado_em TIMESTAMPTZ,
    motivo_estorno VARCHAR(255),
    CONSTRAINT uq_pagamento_conta_pagar_owner_id UNIQUE (owner_user_id, id),
    CONSTRAINT fk_pagamento_conta_pagar_obrigacao FOREIGN KEY (owner_user_id, conta_pagar_id)
        REFERENCES conta_pagar (owner_user_id, id),
    CONSTRAINT fk_pagamento_conta_pagar_conta FOREIGN KEY (owner_user_id, conta_financeira_id)
        REFERENCES conta_financeira (owner_user_id, id),
    CONSTRAINT ck_pagamento_conta_pagar_estorno CHECK (
        (estornado_em IS NULL AND motivo_estorno IS NULL)
        OR (estornado_em IS NOT NULL AND motivo_estorno IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_pagamento_conta_pagar_ativo
    ON pagamento_conta_pagar (conta_pagar_id) WHERE estornado_em IS NULL;
CREATE INDEX idx_pagamento_conta_pagar_conta
    ON pagamento_conta_pagar (owner_user_id, conta_financeira_id)
    WHERE estornado_em IS NULL;
