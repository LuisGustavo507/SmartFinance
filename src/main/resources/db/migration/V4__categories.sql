CREATE TABLE categoria (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id INTEGER NOT NULL REFERENCES usuario (id),
    nome VARCHAR(120) NOT NULL CHECK (length(btrim(nome)) > 0 AND nome = btrim(nome)),
    natureza VARCHAR(20) NOT NULL CHECK (natureza IN ('RECEITA', 'DESPESA')),
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    criada_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_categoria_owner_natureza_nome UNIQUE (owner_user_id, natureza, nome),
    CONSTRAINT uq_categoria_owner_id UNIQUE (owner_user_id, id)
);
