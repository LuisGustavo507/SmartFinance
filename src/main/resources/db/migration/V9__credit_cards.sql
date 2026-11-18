INSERT INTO instituicao_financeira (codigo, nome, ativa)
    VALUES ('OUTRA', 'Outra instituição', TRUE);

CREATE TABLE cartao_credito (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id INTEGER NOT NULL REFERENCES usuario (id),
    conta_pagamento_padrao_id INTEGER,
    nome VARCHAR(120) NOT NULL
        CHECK (length(btrim(nome)) > 0 AND nome = btrim(nome)),
    instituicao_id INTEGER NOT NULL REFERENCES instituicao_financeira (id),
    instituicao_nome_outro VARCHAR(120),
    dia_fechamento SMALLINT NOT NULL
        CHECK (dia_fechamento BETWEEN 1 AND 28),
    dia_vencimento SMALLINT NOT NULL
        CHECK (dia_vencimento BETWEEN 1 AND 28),
    limite_total NUMERIC(19, 2) CHECK (limite_total > 0),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_cartao_credito_owner_nome UNIQUE (owner_user_id, nome),
    CONSTRAINT uq_cartao_credito_owner_id UNIQUE (owner_user_id, id),
    CONSTRAINT fk_cartao_credito_conta_padrao
        FOREIGN KEY (owner_user_id, conta_pagamento_padrao_id)
        REFERENCES conta_financeira (owner_user_id, id),
    CONSTRAINT ck_cartao_credito_nome_outro
        CHECK (instituicao_nome_outro IS NULL
            OR (length(btrim(instituicao_nome_outro)) > 0
                AND instituicao_nome_outro = btrim(instituicao_nome_outro)))
);

CREATE TABLE fatura_cartao (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id INTEGER NOT NULL REFERENCES usuario (id),
    cartao_id INTEGER NOT NULL,
    mes_referencia DATE NOT NULL,
    data_fechamento DATE NOT NULL,
    data_vencimento DATE NOT NULL CHECK (data_vencimento >= data_fechamento),
    valor_fechado NUMERIC(19, 2) CHECK (valor_fechado >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN (
        'PROJETADA', 'ABERTA', 'FECHADA', 'PAGA', 'ATRASADA'
    )),
    criado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_fatura_cartao_owner_id UNIQUE (owner_user_id, id),
    CONSTRAINT uq_fatura_cartao_cartao_mes UNIQUE (cartao_id, mes_referencia),
    CONSTRAINT fk_fatura_cartao_cartao
        FOREIGN KEY (owner_user_id, cartao_id)
        REFERENCES cartao_credito (owner_user_id, id)
);

CREATE TABLE compra_cartao (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id INTEGER NOT NULL REFERENCES usuario (id),
    cartao_id INTEGER NOT NULL,
    categoria_id INTEGER NOT NULL,
    entidade_id INTEGER,
    descricao VARCHAR(255) NOT NULL
        CHECK (length(btrim(descricao)) > 0 AND descricao = btrim(descricao)),
    valor_total NUMERIC(19, 2) NOT NULL CHECK (valor_total > 0),
    comprada_em TIMESTAMPTZ NOT NULL,
    quantidade_parcelas SMALLINT NOT NULL CHECK (quantidade_parcelas >= 1),
    necessidade VARCHAR(20) NOT NULL CHECK (necessidade IN (
        'NECESSARIA', 'NEUTRA', 'FUTIL'
    )),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ATIVA', 'CANCELADA', 'ESTORNADA')),
    criado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_compra_cartao_owner_id UNIQUE (owner_user_id, id),
    CONSTRAINT fk_compra_cartao_cartao
        FOREIGN KEY (owner_user_id, cartao_id)
        REFERENCES cartao_credito (owner_user_id, id),
    CONSTRAINT fk_compra_cartao_categoria
        FOREIGN KEY (owner_user_id, categoria_id)
        REFERENCES categoria (owner_user_id, id),
    CONSTRAINT fk_compra_cartao_entidade
        FOREIGN KEY (owner_user_id, entidade_id)
        REFERENCES entidade (owner_user_id, id)
);

CREATE TABLE parcela_cartao (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id INTEGER NOT NULL REFERENCES usuario (id),
    compra_cartao_id INTEGER NOT NULL,
    fatura_cartao_id INTEGER NOT NULL,
    numero_parcela SMALLINT NOT NULL CHECK (numero_parcela >= 1),
    valor NUMERIC(19, 2) NOT NULL CHECK (valor > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ATIVA', 'CANCELADA', 'ESTORNADA')),
    criada_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_parcela_cartao_owner_id UNIQUE (owner_user_id, id),
    CONSTRAINT uq_parcela_cartao_numero UNIQUE (compra_cartao_id, numero_parcela),
    CONSTRAINT fk_parcela_cartao_compra
        FOREIGN KEY (owner_user_id, compra_cartao_id)
        REFERENCES compra_cartao (owner_user_id, id),
    CONSTRAINT fk_parcela_cartao_fatura
        FOREIGN KEY (owner_user_id, fatura_cartao_id)
        REFERENCES fatura_cartao (owner_user_id, id)
);

CREATE INDEX idx_fatura_cartao_owner_mes
    ON fatura_cartao (owner_user_id, mes_referencia);

CREATE INDEX idx_parcela_cartao_fatura
    ON parcela_cartao (fatura_cartao_id);

CREATE INDEX idx_compra_cartao_owner_data
    ON compra_cartao (owner_user_id, comprada_em);
