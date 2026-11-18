CREATE TABLE pagamento_fatura (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id INTEGER NOT NULL REFERENCES usuario (id),
    fatura_cartao_id INTEGER NOT NULL,
    conta_financeira_id INTEGER NOT NULL,
    valor NUMERIC(19, 2) NOT NULL CHECK (valor > 0),
    pago_em TIMESTAMPTZ NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_pagamento_fatura_owner_id UNIQUE (owner_user_id, id),
    CONSTRAINT fk_pagamento_fatura_fatura
        FOREIGN KEY (owner_user_id, fatura_cartao_id)
        REFERENCES fatura_cartao (owner_user_id, id),
    CONSTRAINT fk_pagamento_fatura_conta
        FOREIGN KEY (owner_user_id, conta_financeira_id)
        REFERENCES conta_financeira (owner_user_id, id)
);

CREATE INDEX idx_pagamento_fatura_fatura
    ON pagamento_fatura (fatura_cartao_id);

CREATE INDEX idx_pagamento_fatura_conta
    ON pagamento_fatura (owner_user_id, conta_financeira_id);
