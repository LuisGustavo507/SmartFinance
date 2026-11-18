CREATE TABLE entidade (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id INTEGER NOT NULL REFERENCES usuario (id),
    nome VARCHAR(120) NOT NULL
        CHECK (nome = btrim(nome) AND length(nome) > 0),
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    criada_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_entidade_owner_id UNIQUE (owner_user_id, id)
);

CREATE UNIQUE INDEX uq_entidade_owner_nome_normalizado
    ON entidade (owner_user_id, lower(btrim(nome)));

ALTER TABLE lancamento_financeiro
    ADD COLUMN entidade_id INTEGER,
    ADD CONSTRAINT fk_lancamento_entidade_owner
        FOREIGN KEY (owner_user_id, entidade_id)
        REFERENCES entidade (owner_user_id, id);
