-- Catálogo global de instituições financeiras, compartilhado por todos os usuários.
-- A entrada OUTRA permite informar o nome da instituição até que ela seja
-- incluída globalmente. As demais são pré-cadastradas e podem ser ajustadas
-- em evolução; o código é a chave funcional estável de cada entrada.
INSERT INTO instituicao_financeira (codigo, nome, ativa) VALUES
    ('BANCO_DO_BRASIL', 'Banco do Brasil', TRUE),
    ('ITAU_UNIBANCO', 'Itaú Unibanco', TRUE),
    ('BRADESCO', 'Banco Bradesco', TRUE),
    ('SANTANDER', 'Banco Santander', TRUE),
    ('CAIXA_ECONOMICA_FEDERAL', 'Caixa Econômica Federal', TRUE),
    ('NUBANK', 'Nu Pagamentos (Nubank)', TRUE),
    ('INTER', 'Banco Inter', TRUE)
ON CONFLICT (codigo) DO NOTHING;
