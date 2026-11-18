CREATE TABLE usuario (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ATIVO', 'INATIVO')),
    criado_em TIMESTAMPTZ NOT NULL
);

CREATE TABLE instituicao_financeira (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo VARCHAR(30) NOT NULL UNIQUE,
    nome VARCHAR(120) NOT NULL,
    ativa BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE conta_financeira (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id INTEGER NOT NULL REFERENCES usuario (id),
    nome VARCHAR(120) NOT NULL,
    tipo VARCHAR(30) NOT NULL CHECK (tipo IN (
        'CONTA_CORRENTE', 'POUPANCA', 'CONTA_PAGAMENTO',
        'CARTEIRA_DINHEIRO', 'OUTRA'
    )),
    instituicao_id INTEGER REFERENCES instituicao_financeira (id),
    instituicao_nome_outro VARCHAR(120),
    saldo_inicial NUMERIC(19, 2) NOT NULL DEFAULT 0,
    data_saldo_inicial DATE NOT NULL,
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    criada_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_conta_financeira_owner_nome UNIQUE (owner_user_id, nome),
    CONSTRAINT uq_conta_financeira_owner_id UNIQUE (owner_user_id, id),
    CONSTRAINT ck_carteira_sem_instituicao CHECK (
        tipo <> 'CARTEIRA_DINHEIRO'
        OR (instituicao_id IS NULL AND instituicao_nome_outro IS NULL)
    )
);
