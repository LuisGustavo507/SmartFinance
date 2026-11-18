# Conclusão incremental da V1

Este é o roteiro operacional das lacunas **documentadas** da V1. Antes de
cada etapa, conferir este arquivo, as regras de produto e o comportamento
atual; depois de implementar, validar e registrar evidências aqui, avançar
para a próxima. Não antecipar decisões necessárias apenas em etapas futuras.

Fontes de escopo: Obsidian `Files.md/Smart Finance/docs/Produto.md`,
`Regras de Negocio.md`, `Modelagem de Dados.md` e `Arquitetura.md`.
O `README.md` deste repositório descreve o que já foi entregue; divergências
entre especificação e implementação devem ser explicitadas antes de corrigir.

## Regras de execução

- Estados: `PENDENTE`, `EM ANDAMENTO`, `VALIDADA` ou `BLOQUEADA`. Só marcar
  `VALIDADA` depois de testes backend/frontend pertinentes, build, fluxo
  observado no navegador quando aplicável e atualização dos READMEs.
- Cada fatia deve preservar isolamento entre usuários, CSRF, histórico,
  valores monetários e regras de datas em UTC. Migrations SQL Flyway somente
  quando o schema precisar mudar; regenerar JPA a partir do schema.
- Registrar abaixo descobertas, decisões aprovadas, resultados e bloqueios.
  Dúvidas que não afetem a fatia atual não impedem o avanço.
- A revisão **visual final** fica para depois da implementação funcional da
  V1, conforme decisão do usuário. Testes automáticos e verificação funcional
  em navegador continuam necessários em cada fatia.
- Fora desta V1: conciliação/integração bancária, edição dos valores ou da
  conta de lançamentos efetivados, estorno de lançamentos comuns, pagamentos
  parciais de fatura, devolução parcial, voz, múltiplas moedas, contabilidade
  formal e projeção futura de saldo por conta. Edição/exclusão de carteira e
  renomeação de categoria não eram requisitos expressos no plano original;
  a solicitação posterior em `Solicitacao de alteracoes.md` autorizou a
  edição de nome, a alteração de natureza e a exclusão sem vínculos.

## Base já entregue

Autenticação, contas com tipo e instituição, saldo estimado, categorias e
entidades independentes, lançamentos realizados/previstos/séries,
transferências, cartões/compras/faturas/pagamento/estorno integral,
classificação de despesas e resumo mensal por necessidade, categoria e
entidade, com relatório por período e gráficos ao longo do tempo. Na última
validação: 56 testes backend, 34 frontend e build Angular; a aprovação
visual permanece pendente.

## Etapas restantes

### 1. Desativação de categoria — VALIDADA

**Fonte:** `Regras de Negocio.md` → Categorias: categoria usada não é
excluída, apenas desativada; natureza não muda após o primeiro uso.

**Entrega:** categoria própria pode ser desativada sem perder lançamentos e
compras históricos; deixa de aparecer nas novas associações. Reativar é uma
extensão reversível recomendada para o mesmo controle de estado, não uma
exigência expressa. Sem edição de nome/natureza nem exclusão nesta etapa.

1. Teste de integração do estado, escopo do proprietário, CSRF, corpo inválido
   e repetição; confirmar histórico e bloqueio de novos lançamentos/compras.
2. Porta, caso de uso, persistência e operação HTTP de estado. O schema já
   possui `categoria.ativa`; não há migration prevista.
3. Ações na página de categorias e seletores de lançamentos/compras;
   categorias inativas continuam visíveis no histórico.
4. Testes frontend, suíte completa, build, fluxo navegador e READMEs.

**Pronto quando:** uma categoria utilizada pode ser desativada e consultada;
outra pessoa não a altera; novas associações falham, registros antigos e
seus totais permanecem iguais. A reativação, se implementada, restabelece
novas associações.

### 2. Entidade no próprio registro — VALIDADA

**Fonte:** `Produto.md` → Escopo e `Regras de Negocio.md` → Entidades de
contraparte. Antes era preciso sair do lançamento/compra para cadastrá-la.

Permitir criar entidade com nome no fluxo de receita, despesa, previsão,
série e compra no cartão, associando-a imediatamente. Preservar nome único
por usuário, entidade opcional e propriedade; evitar entidade órfã se o
registro financeiro falhar. Definir o menor contrato transacional compatível
com os casos de uso atuais. Testar duplicidade, falha atômica e interface.

**Entregue:** campo `newEntityName` nas quatro operações de registro
(lançamento realizado, previsto, série e compra no cartão). A entidade é
criada na mesma transação do registro; `entityId` e `newEntityName` são
mutuamente exclusivos (`400`), nome repetido retorna `409` e registro
recusado não deixa entidade órfã.

### 3. Correção isolada de entidade histórica — VALIDADA

**Fonte:** `Regras de Negocio.md` → Entidades de contraparte.

Permitir trocar/remover somente o vínculo com entidade em lançamento
efetivado e compra mesmo após fechamento; não reabrir lançamentos/faturas,
alterar valores, datas, conta, categoria, parcelas nem saldo. Entidade inativa
exige reativação para nova associação. Cobrir dono, histórico, séries,
faturas e interface.

**Entregue:** `PUT /api/entries/{id}/entity` (um lançamento) e
`PUT /api/card-purchases/{id}/entity` (a compra inteira, todas as
parcelas). `entityId: null` remove o vínculo; entidade inativa ou alheia
retorna `404`. Nada além da identificação muda: teste compara valor, data,
status, total fechado e parcelas antes e depois da correção.

### 4. Visão mensal por categoria e natureza — VALIDADA

**Fonte:** `Regras de Negocio.md` → Visão mensal e `Produto.md` → Objetivo.

Somar receitas/despesas realizadas e previstas por categoria e natureza,
incluindo parcelas do cartão no mês da fatura. Preservar o agrupamento atual
por necessidade, os totais globais e créditos separados. Exibir o resultado
na interface; reconciliar somas dos grupos com o resumo geral em testes.

**Entregue:** `categoryTotals` no resumo mensal, com categoria, natureza,
realizadas e previstas; parcelas de cartão entram pelo mês da fatura e
seguem as mesmas regras de status dos totais (cancelada fora, estornada
realizada). Exibido na página de lançamentos e na home. Teste reconcilia a
soma dos grupos com os totais do mês, em lançamentos comuns e em compra de
cartão.

### 5. Consulta por entidade e período, incluindo cartão — VALIDADA

**Fonte:** `Produto.md` → Escopo, `Regras de Negocio.md` → Visão mensal e
`Arquitetura.md` → Visão mensal.

Estender a consulta além do filtro atual de lançamentos por mês: receitas
e despesas por entidade no período, incluindo parcelas pelo mês da fatura.
Créditos não são distribuídos por entidade, nem alteram o total individual;
bruto/créditos/líquido consolidados da fatura não mudam com o filtro. Antes
de implementar, decidir a menor representação de “período” (recomendação:
intervalo inclusivo de meses) e validar a forma de exibição dos indicadores
com o comportamento real da UI.

**Duas entregas:** 5a — resumo mensal aceita `entityId` (totais e grupos
filtrados, com parcelas de cartão pela fatura); 5b — relatório por entidade
em intervalo de meses, com receitas e despesas separadas e grupo “sem
entidade” para reconciliar com o período.

**Entregue:** 5a — `GET /api/monthly-summary?month&entityId` filtra totais,
grupos por necessidade e por categoria; créditos de estorno **não** são
distribuídos por entidade e a composição da fatura (bruto/créditos/líquido)
permanece intacta. 5b — `GET /api/reports/entity-period?from&to` devolve
receitas e despesas realizadas e previstas por entidade no período, com
grupo “Sem entidade”; período invertido → `400`. Página nova “Relatórios”
(`/relatorios`) no menu.

### 6. Cadastro de conta conforme o modelo V1 — VALIDADA

**Fonte:** `Produto.md` → Escopo, `Modelagem de Dados.md` →
`conta_financeira`, `Arquitetura.md` → Instituições financeiras.

Hoje o cadastro fixa `CARTEIRA_DINHEIRO`. Completar escolha dos tipos de
conta modelados e associação opcional/obrigatória de instituição conforme
tipo, incluindo `OUTRA` com nome informado. Contas antigas devem manter
seus dados e saldos. O catálogo global possui apenas `OUTRA`; escolher quais
instituições pré-cadastrar **quando esta etapa começar**, sem inventar
nomes agora. Validar migrations necessárias, conta alheia/inativa, UI e
regressão de saldo.

**Entregue:** `POST /api/wallets` aceita `type` (padrão
`CARTEIRA_DINHEIRO`) e `institutionName`; carteira em dinheiro fica sem
instituição, os demais tipos exigem instituição (`400` sem ela). O nome é
resolvido contra o catálogo global: instituição conhecida vira FK; nome
desconhecido cai na entrada `OUTRA` com o nome informado. `GET
/api/institutions` expõe o catálogo (V13 semeia `OUTRA` + sete instituições
provisórias). Contas de qualquer tipo entram na lista e no saldo estimado.

### 7. Relatórios e gráficos ao longo do tempo — VALIDADA

**Fonte:** `Produto.md` → Objetivo. Usar as consultas mensais consolidadas
das etapas 4–5 como base, sem materializar balanços nem criar novas regras
financeiras no frontend. Escolher um primeiro gráfico e período verificáveis
com dados de meses distintos; testar números, meses vazios, isolamento e
leitura em tela pequena. Detalhar só após conhecer os contratos das etapas
anteriores.

**Entregue:** `GET /api/reports/monthly?from&to` devolve um ponto por mês
do período — receitas/despesas realizadas e previstas, resultado realizado e
projetado — com meses sem movimento preenchidos com zero. O gráfico é SVG
próprio (sem dependência externa), desenhado a partir dos números do
backend, com rótulo acessível e lista de meses com os valores.

### 8. Fechamento funcional da V1 — VALIDADA

Percorrer fluxos principais de ponta a ponta, estados vazios e erros,
revisar isolamento/CSRF, migrações do zero, testes completos, build,
responsividade funcional e consistência dos READMEs. Comparar cada item
do escopo explícito com a implementação e registrar lacunas restantes.
Entregar o sistema para a **avaliação visual do usuário**, sem chamar a
aparência de aprovada antes disso.

**Entregue:** percurso completo com usuário novo (cadastro → login → home
vazio → categoria → conta → despesa com entidade criada junto → cartão →
compra parcelada → resumo com agrupamentos → filtro por entidade →
relatórios com gráfico e quebra por entidade); estados vazios em todas as
telas; 13 migrations aplicadas do zero em banco limpo a cada execução da
suíte; `401` em rotas sem sessão e CSRF coberto por teste em cada escrita;
Lighthouse com acessibilidade 1,0 e boas práticas 1,0 na página de
relatórios. Cartão passou a referenciar instituições do catálogo (igual às
contas), com a entrada `OUTRA` para nomes desconhecidos.

**Lacunas registradas no README** (fora do escopo documentado da V1 ou
adiadas pela própria documentação): edição/exclusão de contas, edição de
nome/natureza de categoria, edição de valores de lançamentos efetivados,
conciliação bancária, pagamento parcial, devolução parcial, voz, múltiplas
moedas, contabilidade formal, saldo futuro projetado, fluxo de solicitação
de instituição no catálogo e lista de instituições pré-cadastradas
(provisória).

## Registro de validação e descobertas

| Etapa | Estado | Evidência / decisão / próximo passo |
|---|---|---|
| 1 | VALIDADA | 44 testes backend, 29 frontend, build Angular e navegador: desativação/reativação de categoria utilizada; opções novas filtradas; histórico e totais preservados; usuário alheio e CSRF rejeitados. Sem migration. |
| 2 | VALIDADA | 47 testes backend, 30 frontend, build Angular e navegador: entidade criada junto com o lançamento (realizado, previsto, série e compra), nome repetido `409`, `entityId` + `newEntityName` `400`, compra recusada não deixa entidade órfã, campo limpo após o registro. Sem migration. |
| 3 | VALIDADA | 49 testes backend, 32 frontend, build Angular e navegador: vínculo de entidade corrigido em lançamento efetivado e em compra com fatura fechada, sem alterar valor, data, status, parcelas ou total; `entityId: null` remove; entidade inativa/alheia `404`. Corrigido teste de pagamento dependente do horário (pagava com `T12:00:00Z` do dia corrente, futuro entre 00:00 e 12:00 UTC). |
| 4 | VALIDADA | 50 testes backend, 32 frontend, build Angular e navegador: agrupamento por categoria e natureza no resumo, com parcelas de cartão pelo mês da fatura, reconciliado com os totais. |
| 5 | VALIDADA | 53 testes backend, 34 frontend, build Angular e navegador: resumo mensal filtrado por entidade (com parcelas de cartão) e nova página de Relatórios com receitas/despesas por entidade em intervalo de meses, incluindo grupo “Sem entidade”. |
| 6 | VALIDADA | 54 testes backend, 34 frontend, build Angular e navegador: conta com tipo e instituição, catálogo global (`GET /api/institutions`), entrada `OUTRA` para nome desconhecido, carteira em dinheiro sem instituição, tipos e nomes inválidos recusados, saldo de contas não-cinheiro funcionando. Migration V13 (só seed). |
| 7 | VALIDADA | 55 testes backend, 34 frontend, build Angular e navegador: série mensal com meses vazios em zero, gráfico SVG de receitas/despesas realizadas por mês e lista de valores; resultado realizado e projetado vêm calculados no backend. |
| 8 | VALIDADA | 56 testes backend, 34 frontend, build Angular e percurso completo no navegador com usuário novo; estados vazios em todas as telas; migrations do zero (13) a cada execução da suíte; `401` sem sessão; Lighthouse acessibilidade 1,0 e boas práticas 1,0. Cartão passou a usar o catálogo de instituições. Lacunas conhecidas registradas no README. |

### Caution de execução

Ao mudar assinaturas de casos de uso, o plugin de compilação do Maven pode
considerar as classes de teste desatualizadas e não recompilá-las. Depois de
alterar assinaturas Java, use `find src/test/java -name '*.java' -exec touch
{} +` antes de rodar os testes, ou rode `clean`, para não validar código
antigo.

## Encerramento

Todas as etapas documentadas foram implementadas e validadas. O sistema
entrega a V1 funcional; falta apenas a **avaliação visual do usuário**, que
fica fora do ciclo automático de implementação. Novas entradas de escopo
devem ser adicionadas a este arquivo antes de virar fatia.
