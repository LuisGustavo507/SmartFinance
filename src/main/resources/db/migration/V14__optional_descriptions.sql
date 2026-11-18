-- Empty string represents an omitted description, keeping existing generated mappings.
ALTER TABLE lancamento_financeiro DROP CONSTRAINT lancamento_financeiro_descricao_check;
ALTER TABLE compra_cartao DROP CONSTRAINT compra_cartao_descricao_check;
ALTER TABLE compra_cartao ADD CONSTRAINT ck_compra_descricao_aparada
    CHECK (descricao = btrim(descricao));
