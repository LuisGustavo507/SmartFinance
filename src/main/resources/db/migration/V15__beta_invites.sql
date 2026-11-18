CREATE TABLE convite_beta (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    usado_em TIMESTAMPTZ,
    usado_por INTEGER REFERENCES usuario (id),
    CONSTRAINT uq_convite_beta_email UNIQUE (email),
    CONSTRAINT ck_convite_beta_email_minusculo CHECK (email = lower(email))
);
