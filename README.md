# Smart Finance

Java 21, Spring Boot 4.1.2, PostgreSQL 18 e migrations SQL com Flyway.
Há backend e um app Angular 21/PrimeNG com login, home de visão geral e
páginas próprias para carteiras, lançamentos (avulsos, previstos e séries
mensais finitas), cartões de crédito com compras parceladas e faturas,
transferências, categorias e entidades de contraparte.
A interface usa a paleta fornecida (`#E9F1FA`, `#00ABE4`, `#FFFFFF`), menu
lateral no desktop, gaveta no mobile e rotas reais por página; continua
evolutiva e ainda não foi aprovada visualmente como versão final.
Os documentos de produto/regras/arquitetura no Obsidian descrevem o escopo
mais amplo; este README descreve o que o código faz e como executá-lo.

## Estado atual

| Método e rota | Comportamento |
|---|---|
| `GET /api/auth/csrf` | Emite `XSRF-TOKEN`, cookie legível pelo Angular |
| `POST /api/auth/register` | `{ "name", "email", "password" }` → `201` com perfil público |
| `POST /api/auth/login` | `{ "email", "password" }` → `204`, cookies de acesso e refresh |
| `POST /api/auth/refresh` | Troca o refresh antigo por novos cookies → `204` |
| `POST /api/auth/logout` | Revoga o refresh e limpa os cookies → `204` |
| `GET /api/auth/me` | Perfil do usuário autenticado, sem senha |
| `POST /api/wallets` | `{ "name", "type"?, "institutionName"?, "initialBalance", "initialBalanceDate" }` → `201`; `type` padrão `CARTEIRA_DINHEIRO` |
| `GET /api/institutions` | Catálogo global de instituições ativas (sem `ownerUserId`) |
| `GET /api/wallets` e `GET /api/wallets/{id}` | Somente carteiras do usuário autenticado |
| `POST /api/categories` | `{ "name", "kind": "RECEITA" ou "DESPESA" }` → `201` com categoria |
| `GET /api/categories` | Lista apenas as categorias do usuário autenticado |
| `PATCH /api/categories/{id}/status` | `{ "active": false }` ou `{ "active": true }` → desativa/reativa categoria própria; alheia/inexistente → `404` |
| `PUT /api/categories/{id}` | `{ "name", "kind" }` → nome pode ser alterado sempre; natureza só muda sem vínculos com lançamentos/compras → `409` se vinculada; alheia/inexistente → `404` |
| `DELETE /api/categories/{id}` | Exclui categoria própria sem lançamentos/compras vinculados → `204`; vinculada → `409`; alheia/inexistente → `404` |
| `POST /api/entities` | `{ "name" }` → `201` com contraparte privada |
| `GET /api/entities` | Lista somente as entidades do usuário autenticado |
| `PATCH /api/entities/{id}/status` | `{ "active": false }` ou `{ "active": true }` → entidade atualizada, `404` se alheia/inexistente |
| `PUT /api/entities/{id}` | `{ "name": "Novo nome" }` → entidade renomeada, `409` se nome já usado |
| `DELETE /api/entities/{id}` | Exclui entidade própria sem vínculos → `204`; vinculada → `409`; alheia/inexistente → `404` |
| `POST /api/entries` | `{ "walletId", "categoryId", "description", "amount", "occurredOn", "necessity"?, "newEntityName"? }` → `201`; necessidade obrigatória para despesa |
| `POST /api/entries/planned` | `{ "walletId", "categoryId", "description", "amount", "expectedOn", "necessity"?, "newEntityName"? }` → `201` |
| `POST /api/entries/series` | `{ "walletId", "categoryId", "description", "amount", "firstExpectedOn", "totalOccurrences", "entityId"?, "necessity"?, "newEntityName"? }` → `201` com todas as ocorrências |
| `GET /api/entries/series/{seriesId}` | Todas as ocorrências da série do usuário, inclusive canceladas; alheia/inexistente → `404` |
| `PUT /api/entries/{id}` | Edita um lançamento `PREVISTO` próprio: `{ "walletId", "categoryId", "description", "amount", "expectedOn", "entityId"? }` → `200`; efetivado/cancelado → `409` |
| `PUT /api/entries/series/{seriesId}/occurrences/{occurrenceNumber}` | Edita esta ocorrência e as futuras `PREVISTO` da série → `200` com as ocorrências atualizadas; base não editável → `409` |
| `POST /api/entries/{id}/cancel` | Cancela lançamento `PREVISTO` próprio → `204`; já processado → `409` |
| `PUT /api/entries/{id}/entity` | `{ "entityId": 5 }` ou `{ "entityId": null }` → corrige **somente** a entidade do lançamento, inclusive já efetivado; alheio/inexistente → `404` |
| `GET /api/entries?month=YYYY-MM[&entityId=ID]` | Realizados e previstos do usuário no mês de referência; exclui cancelados |
| `GET /api/monthly-summary?month=YYYY-MM` | Totais realizados, previstos e resultado projetado do mês, com grupos por necessidade e por categoria |
| `GET /api/monthly-summary?month=YYYY-MM&entityId=ID` | Mesmos totais **da entidade** (lançamentos e parcelas de cartão); créditos não são distribuídos |
| `GET /api/reports/entity-period?from=YYYY-MM&to=YYYY-MM` | Receitas e despesas realizadas e previstas por entidade no período, com grupo “Sem entidade”; `from` depois de `to` → `400` |
| `GET /api/reports/monthly?from=YYYY-MM&to=YYYY-MM` | Série mensal do período (receitas/despesas realizadas e previstas, resultado realizado e projetado); meses sem movimento vêm com zeros |
| `POST /api/transfers` | `{ "sourceWalletId", "destinationWalletId", "amount", "description", "transferredAt" }` → `201` |
| `GET /api/transfers?month=YYYY-MM` | Transferências do usuário no mês do instante ocorrido em UTC |
| `POST /api/cards` | `{ "name", "institutionName", "closingDay", "dueDay", "creditLimit"?, "defaultPaymentWalletId"? }` → `201` com o cartão |
| `GET /api/cards` e `GET /api/cards/{id}` | Somente cartões do usuário autenticado; alheio/inexistente → `404` |
| `PUT /api/cards/{id}` | Atualiza nome, instituição, limite e conta padrão; dias do ciclo só mudam sem compra/fatura → `409` |
| `PATCH /api/cards/{id}/status` | `{ "active": false }` ou `{ "active": true }` → desativa/reativa cartão próprio |
| `POST /api/card-purchases` | `{ "cardId", "categoryId", "description", "amount", "purchasedOn", "installments", "entityId"?, "necessity", "newEntityName"? }` → `201` com a compra e as parcelas |
| `POST /api/card-purchases/{id}/reversal` | Estorno integral da compra própria → `200` com parcelas atualizadas; alheia/inexistente → `404`; repetição não duplica crédito |
| `PUT /api/card-purchases/{id}/entity` | `{ "entityId": 5 }` ou `{ "entityId": null }` → corrige **somente** a entidade da compra (vale para todas as parcelas), inclusive com fatura fechada |
| `GET /api/card-credits?month=YYYY-MM` | Créditos gerados no mês UTC, com valor original e saldo ainda disponível, somente do usuário |
| `GET /api/card-statements?month=YYYY-MM` | Faturas do usuário no mês de vencimento, com parcelas, fechamento, vencimento e total |
| `GET /api/card-statements/{id}` | Fatura própria com as parcelas; alheia/inexistente → `404` |
| `POST /api/card-statements/{id}/payments` | `{ "walletId", "paidOn" }` → `201` com o pagamento; fatura não fechada/vencida → `409`, pagamento antecipado → `400` |

Exemplo do cadastro: `{ "name": "Ana", "email": "ana@example.test",
"password": "uma-senha-longa" }`. Conta:
`{ "name": "Dinheiro", "initialBalance": 25.50,
"initialBalanceDate": "2026-01-01" }`. A data inicial é obrigatória e valores
monetários têm no máximo duas casas decimais. `ownerUserId` é determinado
pelo JWT validado (um valor enviado pelo cliente é ignorado).

A conta tem **tipo** (`CARTEIRA_DINHEIRO`, `CONTA_CORRENTE`, `POUVANCA`,
`CONTA_PAGAMENTO`, `OUTRA`; padrão `CARTEIRA_DINHEIRO`) e **instituição**.
Carteira em dinheiro não tem instituição; qualquer outro tipo exige
`institutionName` (sem ele, `400`). O nome é resolvido contra o catálogo
global `instituicao_financeira`: institutions pré-cadastradas são
referenciadas pelo id, e um nome desconhecido cai na entrada `OUTRA` com o
nome informado. O catálogo tem `OUTRA` e instituições pré-cadastradas
(Banco do Brasil, Itaú Unibanco, Bradesco, Santander, Caixa Econômica
Federal, Nubank e Inter) — lista **provisória**, ajustável em evolução; o
fluxo de solicitação de inclusão ainda não foi definido. Instituições
inativas continuam referenciáveis por registros antigos.
Outra pessoa não consegue listá-la nem consultá-la pelo ID (resposta `404`).
As respostas de carteira incluem `estimatedBalance`: saldo inicial **mais**
receitas realizadas **menos** despesas realizadas **menos** transferências
enviadas **mais** transferências recebidas **menos** pagamentos de fatura de
cartão. É calculado sob demanda a partir
dos fatos registrados, pode ser negativo e não representa saldo bancário
confirmado. Alteração e exclusão de carteiras, edição de lançamentos já
efetivados e conciliação bancária ainda não existem.

Categorias também pertencem sempre ao usuário autenticado: `ownerUserId`
enviado pelo cliente é ignorado. O nome é aparado nas pontas; dentro da
mesma natureza (`kind`), não pode haver nome repetido para o mesmo usuário
(`409`). Nomes iguais em naturezas diferentes ou em usuários diferentes são
permitidos. Categoria recém-criada é ativa. Pode ser desativada e reativada
sem perder o histórico; quando inativa não aceita novos lançamentos nem
compras, mas segue consultável. Seu nome pode ser alterado mesmo após o uso,
refletindo-se também nos agrupamentos históricos. A natureza só muda se
nenhum lançamento nem compra estiver vinculado (inclusive cancelados);
exclusão permanente também exige ausência de vínculos.
Descrições de lançamentos e compras no cartão são opcionais; ausência é
persistida como texto vazio e mostrada como “Sem descrição”.

Um lançamento comum **já ocorrido** exige uma carteira ativa e uma categoria
ativa do próprio usuário; referências inexistentes ou alheias retornam `404`
sem revelar a qual usuário pertencem. A natureza `RECEITA`/`DESPESA` vem
exclusivamente da categoria, nunca do corpo da requisição. Exemplo:
`{ "walletId": 1, "categoryId": 2, "description": "Compra no mercado",
"amount": 25.50, "occurredOn": "2026-01-01", "entityId": 3 }`.
`entityId` é opcional. `necessity` é **obrigatório para novas despesas**
(`NECESSARIA`, `NEUTRA` ou `FUTIL`), incluindo previsões e séries; receitas
não aceitam classificação. A V12 mantém `necessidade = NULL` em despesas
anteriores, sem presumir intenção; elas aparecem como “Não classificada”.
Uma previsão antiga sem classificação pode continuar assim ao ser editada
ou receber classificação; mudar uma receita prevista para despesa exige
classificá-la. Na edição “esta e as futuras”, uma classificação escolhida
alcança apenas as ocorrências previstas, preservando as processadas e
canceladas. O valor deve ser maior que
zero e ter até duas casas decimais. A data real não pode ser anterior à data
do saldo inicial da carteira nem posterior ao dia corrente **no fuso de
negócio** (`America/Sao_Paulo`, configurável por
`smartfinance.time-zone`). O mês de referência é
o mês de `occurredOn`, inclusive para gastos retroativos; `createdAt` e
`effectiveAt` registram o instante do cadastro. O status é `EFETIVADO`.
O usuário autenticado é o proprietário, independentemente de
`ownerUserId` enviado pelo cliente. A listagem filtra por mês e proprietário.
Lançamentos `PREVISTO` usam a mesma carteira, categoria e entidade opcional,
mas recebem `expectedOn` em vez de `occurredOn`. A data prevista precisa
ser **posterior ao dia atual no fuso de negócio** e não anterior ao início
da carteira.
Até a efetivação, não afetam o saldo estimado nem os totais realizados.
Podem ser cancelados enquanto previstos; um lançamento alheio retorna `404`,
um já efetivado ou cancelado retorna `409`. Um lançamento ainda `PREVISTO`
também pode ser editado: carteira, categoria, descrição, valor, entidade e
data prevista podem mudar, mas a nova data deve continuar futura no fuso de
negócio e a natureza segue a categoria escolhida. Lançamentos `EFETIVADO`
ou `CANCELADO` não são editáveis e retornam `409`; assim o histórico
realizado e o saldo não mudam retroativamente. O job roda diariamente às
**00:05 de `America/Sao_Paulo`** e na inicialização da aplicação para
recuperar dias perdidos. Ele efetiva cada previsto vencido uma única vez:
`occurredOn` vira `expectedOn`, `effectiveAt` marca quando o job rodou e o
mês de referência continua sendo o da data prevista. O cancelado não é
processado nem aparece na listagem comum. O job e o cancelamento usam
atualização condicional para evitar dupla transição. O fuso é configurável
por `smartfinance.time-zone`; timestamps persistidos e JWT continuam em UTC.
Estorno e conciliação bancária ainda não existem.

Uma série mensal finita é criada em uma única transação com **1 a 120**
ocorrências, todas `PREVISTO` inicialmente. A primeira data deve ser futura
no fuso de negócio e não anteceder o saldo inicial da carteira. Cada
ocorrência é um lançamento independente com `seriesId`, `occurrenceNumber`
(de 1 ao total) e `totalOccurrences`; usa a mesma carteira, categoria,
valor, descrição e entidade opcional. A data de cada mês é calculada a partir do **dia da
primeira data**, sem deslocamento cumulativo: uma série iniciada em 31/01
usa o último dia de fevereiro e volta ao dia 31 em março. Séries além de
9999-12 são rejeitadas. Esta convenção de ajuste de dia é **provisória** e
deve ser confirmada antes de depender dela para um produto em produção.
As ocorrências são listadas por mês, com consulta da série completa em
ordem de ocorrência. Cada uma pode ser cancelada individualmente enquanto
prevista; o job diário existente efetiva as vencidas, preservando vínculo e
numeração. Previstas entram no resultado projetado, mas não no saldo
estimado até a efetivação. A edição de uma ocorrência avulsa altera apenas
aquela ocorrência. A ação “esta e as futuras” exige que a ocorrência
selecionada esteja `PREVISTO` e atualiza todas as ocorrências `PREVISTO` de
numeração igual ou superior; `EFETIVADO` e `CANCELADO` são preservados. A nova
data ancora novamente o dia mensal e o ajuste de fim de mês é recalculado para
cada ocorrência editada. Tudo ocorre em uma transação; se alguma ocorrência
deixar de ser editável no meio do caminho, nada é alterado.

Transferências registram apenas movimentações **já ocorridas** entre duas
carteiras distintas, ativas e do mesmo usuário. `transferredAt` é um instante
com offset (por exemplo, `"2026-01-01T12:00:00-03:00"`); o backend normaliza
para UTC e rejeita instantes futuros ou anteriores às datas dos saldos iniciais
de qualquer das carteiras, considerando o dia em UTC. `amount` é positivo,
com até duas casas decimais; `description` é opcional. O proprietário vem da
sessão; carteira alheia/inexistente/inativa retorna `404`. Saldo insuficiente
na origem **não** impede a transferência e pode torná-lo negativo. A operação
gera um registro único e dois efeitos no saldo (saída e entrada), não é
receita nem despesa e não integra os totais mensais. A listagem usa o mês do
instante da transferência **em UTC**, não o mês do offset recebido.
Estorno de transferência ainda não existe.

### Cartões de crédito

O cartão registra **nome único por usuário** (`409` quando repete), instituição
em texto livre, dia de fechamento e dia de vencimento **entre 1 e 28**, limite
opcional apenas informativo e conta de pagamento padrão opcional (precisa ser
uma carteira do próprio usuário; alheia ou inexistente → `404`). O catálogo de
instituições ainda tem somente a entrada `OUTRA`, então a instituição digitada
pelo usuário é gravada como nome próprio do cartão. É possível editar nome,
instituição, limite e conta padrão, bem como ativar/desativar. Os dias de
fechamento e vencimento só podem mudar **antes de qualquer compra ou fatura**
(`409` após isso); o cartão inativo não recebe novas compras, mas mantém
faturas, estornos e pagamentos disponíveis.

A compra no cartão exige cartão **ativo** do próprio usuário (inativo → `409`;
alheio ou inexistente → `404`), categoria **de despesa** ativa (receita,
inativa ou alheia → `400` ou `404`), descrição, valor positivo com até duas
casas decimais, data da compra, de 1 a 120 parcelas, entidade opcional (própria
e ativa) e necessidade `NECESSARIA`, `NEUTRA` ou `FUTIL`. O total é distribuído
em centavos: cada parcela recebe o quociente inteiro e os centavos restantes
vão, um a um, às primeiras parcelas; a quantidade de parcelas não pode
exceder o total em centavos, para que nenhuma parcela fique zerada.

A fatura que recebe a compra é a que **fecha no primeiro dia de fechamento
igual ou posterior à data da compra**; o vencimento é o dia de vencimento do
mês da fatura e, quando o dia de vencimento não é maior que o de fechamento,
o vencimento cai no mês seguinte. O ciclo da fatura vai do dia seguinte ao
fechamento anterior até o dia de fechamento, inclusive. A fatura
pertence ao **mês do vencimento** e há no máximo uma por cartão e mês. A
parcela 1 fica no ciclo da compra e as seguintes em ciclos mensais
consecutivos.

Os estados de fatura são `PROJETADA` (ciclo ainda não começou), `ABERTA`
(ciclo em curso), `FECHADA` (ciclo encerrado, aguardando pagamento), `PAGA`
(quitada) e `ATRASADA` (vencida sem quitação). As transições são dirigidas
pelas **datas de calendário** (com o dia corrente no fuso de negócio), não
por um botão: o ciclo que começou vira aberto, o ciclo que terminou vira
fechado — ou **pago com valor zero** quando não há cobrança ativa — e a
fatura fechada que passou do vencimento vira atrasada. Um
job diário (00:10 de `America/Sao_Paulo`) e a inicialização da aplicação
aplicam essas transições; a listagem de faturas, o registro de compra e o
pagamento também as regularizam antes de decidir, então o atraso do job não
amplia ciclo nenhum. O total da fatura aberta é a soma das parcelas ativas e
`valorFechado` guarda o total líquido consolidado no fechamento.

O pagamento é **único e integral**, feito por uma carteira do próprio usuário
(alheia ou inativa → `404`): `POST /api/card-statements/{id}/payments` com
`{ "walletId", "paidOn" }` devolve `201` e marca a fatura como `PAGA`. Só uma
fatura `FECHADA` ou `ATRASADA` pode ser paga (outro estado → `409`), o valor
pago é sempre o total fechado (um valor diferente → `400`) e a data do
pagamento **não pode ser antecipada** ao fechamento (precisa ser igual ou
posterior ao dia seguinte de `dataFechamento`), nem ser futura, nem anteceder
o saldo inicial da conta. O pagamento **não cria despesa**: ele reduz o
saldo estimado da carteira que pagou. Uma fatura sem cobranças ativas é paga
automaticamente com valor zero e sem registro de pagamento.

Compra no cartão **não move o saldo** de nenhuma carteira: o dinheiro sai
quando a fatura for paga. As parcelas de faturas `PROJETADA`/`ABERTA` entram
como **despesas previstas** do mês de vencimento em `/api/monthly-summary`;
parcelas de faturas `FECHADA`, `ATRASADA` ou `PAGA` contam como **despesas
realizadas** do mesmo mês, independentemente de o pagamento já ter ocorrido.
O **estorno integral** de compra é idempotente: parcelas de faturas
`PROJETADA`/`ABERTA` tornam-se `CANCELADA`, deixam a previsão e não geram
crédito; parcelas de faturas `FECHADA`/`ATRASADA`/`PAGA` tornam-se
`ESTORNADA`, permanecem nas despesas realizadas originais e geram **um único
crédito por compra** pela soma dessas parcelas. Se todas forem canceladas, a
compra fica `CANCELADA`; caso alguma já estivesse fechada, `ESTORNADA`.
Crédito não entra como receita, não reduz despesas nem movimenta carteiras.
O mês UTC da confirmação mostra `reversalCreditsGenerated` separadamente.

Créditos do mesmo cartão são previstos nas faturas ainda não fechadas por
ordem de criação, com desempate por ID; uma fatura nunca recebe mais crédito
que suas cobranças ativas e o excedente segue para a próxima. A projeção
(`projectedCredits`) pode mudar até o fechamento; no fechamento os abatimentos
ficam em `aplicacao_credito` e em `appliedCredits`, e `closedAmount` guarda o
líquido. O resumo mensal mostra `reversalCreditsApplied` separadamente no mês
de vencimento da fatura. Meses sem fatura com cobrança deixam o crédito
disponível para faturas futuras. Fatura de líquido zero passa a `PAGA` sem
pagamento. Uma fatura já fechada nunca é reaberta nem recalculada por estorno.

Ainda não existem devolução parcial, múltiplos pagamentos parciais
e o catálogo de instituições além da entrada `OUTRA`. Uma compra cujo ciclo
já terminou é recusada (`409`) em vez de
entrar em uma fatura encerrada.

Entidades de contraparte identificam opcionalmente uma pessoa ou
estabelecimento, sem alterar valor, categoria, saldo ou natureza. O nome é
aparado nas pontas e único **sem diferenciar maiúsculas/minúsculas** para o
mesmo usuário, inclusive se estiver inativo (`409`); nomes iguais são
permitidos entre usuários diferentes. Referência a entidade alheia ou
inativa ao criar lançamento retorna `404`. Lançamentos já associados
preservam o vínculo se ela ficar inativa; a listagem usa **o nome atual**
da entidade. O filtro `entityId` afeta a lista de lançamentos **e** o
resumo mensal (`/api/monthly-summary?month=YYYY-MM&entityId=ID`), que passa
a mostrar receitas e despesas da entidade, incluindo parcelas de cartão
pelo mês da fatura.
`PATCH /api/entities/{id}/status` permite desativar e reativar entidades
do próprio usuário. Uma entidade inativa continua visível na lista e no
filtro histórico, mas não pode ser escolhida para novos lançamentos; ao
reativar, pode ser associada novamente. A operação repetida mantém o mesmo
estado. `PUT /api/entities/{id}` renomeia uma entidade própria, inclusive
inativa, respeitando a mesma normalização e unicidade do cadastro. O nome
atual aparece também nos lançamentos antigos vinculados, sem alterar valores,
saldo ou resumo mensal. Entidades **sem vínculos** podem ser excluídas,
inclusive se inativas; vínculos realizados ou previstos impedem a exclusão
(`409`), preservando o histórico. A interface solicita confirmação antes
da exclusão.

Uma entidade também pode ser **criada no próprio registro**: lançamentos
realizados, previstos, séries e compras no cartão aceitam `newEntityName`.
O nome passa pelas mesmas regras de normalização e unicidade (repetido →
`409`) e a entidade é criada **na mesma transação** do registro, portanto
nada é gravado se o lançamento ou a compra for recusado. `entityId` e
`newEntityName` são mutuamente exclusivos: informar os dois (ou um nome em
branco) retorna `400`.

O **vínculo** com a entidade pode ser corrigido depois, inclusive em
lançamento já efetivado ou em compra com fatura fechada:
`PUT /api/entries/{id}/entity` age em um lançamento e
`PUT /api/card-purchases/{id}/entity` age na compra inteira (todas as
parcelas). A correção muda apenas a identificação: valores, datas, conta,
categoria, necessidade, parcelas, faturas e saldo permanecem iguais.
`entityId: null` remove o vínculo. Entidade inativa não pode ser
reassociada (`404`) — é preciso reativá-la antes.

O resumo mensal retorna `month` (primeiro dia do mês),
`realizedIncome`, `realizedExpenses` e `realizedResult` para fatos realizados,
além de `plannedIncome` e `plannedExpenses` para ocorrências `PREVISTO`.
`projectedResult` é **receitas realizadas + previstas − despesas realizadas
− previstas**. O mês vazio retorna zeros; cancelados não entram. Inclui as
parcelas de cartão em faturas `PROJETADA`/`ABERTA` como despesas previstas e as
parcelas de faturas `FECHADA`/`ATRASADA`/`PAGA` como despesas realizadas, do
mês de vencimento. `reversalCreditsGenerated` e
`reversalCreditsApplied` aparecem à parte, sem alterar o resultado mensal.
`categoryTotals` agrupa o mês por **categoria e natureza**, com
`realizedIncome`, `realizedExpenses`, `plannedIncome` e `plannedExpenses` por
grupo; lançamentos comuns e parcelas de cartão entram pelo mesmo critério do
total geral (parcela no mês da fatura, `ESTORNADA` continua realizada e
`CANCELADA` de ciclo aberto fica fora). A soma dos grupos reconcilia com os
totais do mês. `necessityTotals` agrupa despesas comuns e parcelas de cartão por
`necessity` (`null` para histórico não classificado), com `realizedExpenses`
e `plannedExpenses` por grupo. Aplica as mesmas regras de status e mês dos
totais gerais: parcelas estornadas de faturas fechadas permanecem realizadas,
canceladas de ciclos abertos saem da previsão e créditos não são subtraídos.
Receitas não aparecem nesses grupos; sem despesas, a lista é vazia.
Continua sendo um **resumo parcial**: não inclui transferências, pagamentos
de fatura nem outras fontes futuras. A
filtragem é por mês de referência e proprietário, não pelo mês em que o
registro foi feito.

O cadastro normaliza o email para minúsculas; duplicidade responde `409`.
No beta fechado (`smartfinance.registration.invite-required=true`), apenas
emails presentes em `convite_beta` podem criar conta; fora da lista a resposta
é `403` com `code: "invite_required"`. O convite é consumido na mesma
transação do cadastro e grava `usado_em`/`usado_por`. A senha é armazenada
como hash BCrypt (mínimo de 12 caracteres, máximo de 72 bytes UTF-8) e nunca
aparece nas respostas. O status inicial é `ATIVO`;
alterá-lo para `INATIVO` bloqueia imediatamente novas chamadas protegidas e
renovações.

### Autenticação e cookies

O navegador envia os cookies automaticamente, mas **POST/PUT/PATCH/DELETE
exigem também `X-XSRF-TOKEN`**, igual ao valor de `XSRF-TOKEN`; sem isso o
Spring Security responde `403`, inclusive em login, refresh e logout. O cookie
CSRF é propositalmente legível pelo JavaScript; os cookies `SF_ACCESS` e
`SF_REFRESH` **não** são (`HttpOnly`). Em produção os três são `Secure`, e os
cookies de autenticação têm `SameSite=Lax`; os de autenticação usam `Path=/api`
e `Path=/api/auth`, respectivamente. A implantação web deve servir Angular e
API na **mesma origem HTTPS**. Não guardar tokens em `localStorage`.

O access é um JWT assinado com chave HMAC configurada fora do repositório,
válido por **15 minutos**. Seu `sub` é somente o ID do usuário; o backend
consulta o status da conta em cada requisição. O refresh é uma sequência
aleatória opaca de 256 bits; somente seu hash SHA-256 vai ao banco. A sessão
tem duração **máxima fixa de 7 dias**, não prorrogada ao renovar. Cada refresh
é consumido uma vez; se um refresh antigo reaparecer, a família toda é
revogada e o usuário precisa entrar de novo. Renovações simultâneas são
serializadas no banco. O frontend compartilha a renovação entre requisições
na mesma aba e usa Web Locks para serializar entre abas quando disponível.
Sem Web Locks, evite múltiplas abas durante a renovação: uma reutilização
acidental do refresh revoga a sessão. A interface renova e repete uma única
vez também operações de escrita que recebem `401` antes da execução. Uma
falha de login (`401` no endpoint de login) é exibida como credenciais
inválidas, sem sugerir que uma sessão foi encerrada.

Logout revoga o refresh e limpa os cookies no navegador, mas um access JWT
copiado antes do logout ainda vale até expirar. `401` indica que é preciso
entrar ou renovar; `403` por CSRF não deve disparar refresh automático.
Não há revogação imediata do access por logout; conta `INATIVA` é checada
imediatamente. O servidor não aceita header `Authorization` como substituto
do cookie nesta versão web.

### Proteções para produção

O beta é fechado por lista de emails. Login, cadastro e renovação de sessão
têm rate limit em memória por instância (resposta `429` com `Retry-After`);
login só consome cota em falha. Erros saem como `ProblemDetail` com `code`,
`requestId` e `instance`, e cada linha de log carrega o `X-Request-Id`. A
auditoria registra `login_succeeded`, `login_failed`, `logout`,
`rate_limited`, `registration_rejected`, `user_registered`, entre outros.

HTTPS e cookies `Secure` são obrigatórios em produção. Atrás do Caddy, use
`SMARTFINANCE_TRUST_FORWARDED_FOR=true`; sem proxy confiável, mantenha
`false`. Ainda não há verificação de email: quem se cadastrar primeiro com um
email convidado fica com a conta — mitigado pelo beta pequeno, rate limit e
auditoria. Rate limit e jobs agendados são por instância; não há coordenação
entre réplicas.

### Organização e dados

- `src/main/resources/db/migration/`: V1 (usuário, instituição, conta),
  V2 (sessão e refresh), V3 (ajuste de tipo detectado na validação) e
  V4 (categorias por usuário e natureza), V5 (lançamentos realizados e
  integridade das referências de mesmo proprietário) e V6 (contrapartes
   privadas e associação opcional no lançamento) e V7 (transferências
   entre carteiras do mesmo proprietário) e V8 (identificador e restrições
   de integridade para séries finitas) e V9 (cartões, compras, parcelas e
    faturas de cartão), V10 (pagamento de fatura de cartão) e V11 (créditos de
    estorno e aplicações nas faturas) e V12 (classificação opcional no schema
    para preservar despesas históricas; exigida pela API em novas despesas)
    e V13 (catálogo global de instituições com a entrada `OUTRA` e
    instituições pré-cadastradas), V14 (descrições opcionais em lançamentos e
    compras) e V15 (convites do beta fechado, com consumo único por email).
- `src/main/java/project/smartfinance/infrastructure/persistence/generated/`:
  classes JPA **geradas a partir do schema migrado**, nunca editar à mão.
  Hibernate apenas valida o schema (`ddl-auto=validate`).
- `application/`: casos de uso e portas sem dependência de JPA;
  `infrastructure/persistence/`: adaptadores JPA/JDBC e tradução para o
  domínio; `domain/wallet/`, `domain/category/`, `domain/entry/`,
   `domain/summary/`, `domain/counterparty/`, `domain/transfer/` e
   `domain/card/`: modelos sem Spring;
  `api/`: controllers HTTP. O pacote `project.smartfinance` veio do Initializr
  e ainda pode ser renomeado antes de ampliar o projeto.
- `auth_session` e `auth_refresh_token` armazenam a família revogável,
  expiração e hashes de refresh; não armazenam valores de cookies.
- `frontend/`: Angular 21 standalone e PrimeNG/Aura. O proxy local
  (`frontend/proxy.conf.json`) encaminha `/api` para o backend.

## Banco local (Docker)

O desenvolvimento usa `postgres:18-alpine` com volume Docker no HD
(`/data/SmartFinance/docker`). O contêiner `smartfinance-postgres` não inicia
automaticamente com o sistema:

```sh
docker start smartfinance-postgres
docker stop smartfinance-postgres
```

Ele escuta somente em `127.0.0.1:5432`. As credenciais de desenvolvimento
ficam em `/data/SmartFinance/dev-postgres.env` (permissões `0600`), **fora do
repositório**. O mesmo arquivo contém `SMARTFINANCE_JWT_SECRET` (chave
aleatória em Base64, mínimo 32 bytes) e `SMARTFINANCE_COOKIE_SECURE=false`
**somente para HTTP local**. Em produção, não definir esta exceção; cookies
Secure e HTTPS são obrigatórios. Para recriar o ambiente em outra máquina,
configure um PostgreSQL local, gere uma chave com `openssl rand -base64 48`
e crie um arquivo privado com `POSTGRES_DB`, `POSTGRES_USER`,
`POSTGRES_PASSWORD`, `SMARTFINANCE_JWT_SECRET` e, se for HTTP local,
`SMARTFINANCE_COOKIE_SECURE=false`. Nunca envie esse arquivo para o Git.
`SMARTFINANCE_DB_ENV_FILE` indica um caminho diferente para o script.

Para rodar o backend localmente (o contêiner precisa estar ligado):

```sh
docker start smartfinance-postgres
set -a
. /data/SmartFinance/dev-postgres.env
set +a
export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5432/$POSTGRES_DB"
export SPRING_DATASOURCE_USERNAME="$POSTGRES_USER"
export SPRING_DATASOURCE_PASSWORD="$POSTGRES_PASSWORD"
MAVEN_USER_HOME=/data/SmartFinance/maven-home TMPDIR=/data/SmartFinance/tmp \
  ./mvnw -Dmaven.repo.local=/data/SmartFinance/maven-repository spring-boot:run
```

O backend fica em `http://localhost:8080` enquanto esse comando está ativo.
Não usar essa configuração HTTP na rede pública. `./mvnw test` funciona sem
o contêiner local, pois usa Testcontainers.

## Frontend local

Com o backend ligado em outra janela:

```sh
cd frontend
npm start -- --host 127.0.0.1
```

Abra `http://127.0.0.1:4200`. Use **somente** essa origem no navegador: o
proxy encaminha `/api` a 8080 sem CORS. Em produção não existe servidor Node:
`./mvnw -Pfrontend package` compila o Angular para dentro do jar, e o Spring
Boot serve a SPA e a API na mesma origem. O Caddy cuida do HTTPS e encaminha
tudo para a aplicação (veja [deploy/ORACLE.md](deploy/ORACLE.md)).

O app é mobile-first e usa rotas com carregamento sob demanda:

| Rota | Conteúdo |
|---|---|
| `/login` | Cadastro e login; usuário autenticado é redirecionado para a home |
| `/` | Home com saldo total, totais do mês, últimos lançamentos e atalhos |
| `/carteiras` | Cadastro e saldo estimado de cada carteira |
| `/lancamentos` | Lançamentos do mês, resumo, previsões, séries finitas e edição |
| `/cartoes` | Cadastro de cartão, compra parcelada, faturas do mês e pagamento |
| `/transferencias` | Transferência entre carteiras e listagem do mês UTC |
| `/categorias` | Cadastro e listagem de categorias |
| `/entidades` | Cadastro, renomeação, ativação e exclusão de contrapartes |

Um guard aguarda a restauração da sessão antes de ativar a rota; sem sessão,
rotas internas redirecionam para `/login`. No desktop o menu fica fixo à
esquerda; abaixo de 900 px ele vira uma gaveta com botão de menu. A UI obtém
CSRF, restaura a sessão, cria/lista carteiras, categorias e contrapartes,
ativa/desativa, renomeia e exclui contrapartes sem vínculos, registra
receitas/despesas realizadas ou previstas, cancela previsões, edita uma
previsão ou esta e as futuras de uma série, cria séries
mensais finitas e consulta suas ocorrências, cadastra cartões de crédito,
registra compras no cartão à vista ou parceladas, acompanha as faturas do
mês e paga as faturas fechadas, cria/lista transferências por
mês UTC, filtra os lançamentos do mês por entidade e mostra um resumo mensal
explicitamente parcial com totais realizados, previstos e resultado
projetado. Em `401` de leitura protegida tenta renovar uma vez; `403` não
dispara renovação e uma gravação com access expirado exige novo login
(requisições que alteram estado não são repetidas após `401`). Não usa
`localStorage` para tokens. O build separa as
páginas em chunks; o inicial ainda supera o orçamento padrão por causa do
Angular/PrimeNG e o orçamento local foi ajustado para 600 kB.

Nesta máquina, `frontend/node_modules` aponta para
`/data/SmartFinance/frontend/node_modules` e o cache npm fica em
`/data/SmartFinance/npm-cache`. **Não rode `npm ci` por cima do link:**
o npm substitui o link por uma pasta no SSD. Para instalar/reinstalar no HD,
a partir da **raiz do projeto**:

```sh
mkdir -p /data/SmartFinance/frontend
cp frontend/package.json frontend/package-lock.json /data/SmartFinance/frontend/
npm ci --prefix /data/SmartFinance/frontend --cache /data/SmartFinance/npm-cache
# Se necessário, crie frontend/node_modules como link para
# /data/SmartFinance/frontend/node_modules.
```

Dependências de produção: `npm audit --omit=dev` sem alertas na data desta
entrega. O npm ainda apresenta avisos moderados em ferramentas de
desenvolvimento; revisar periodicamente.

## Gerar classes JPA a partir do schema

Com o contêiner iniciado:

```sh
bash scripts/generate-persistence-model.sh
```

O script inicia a aplicação sem servidor HTTP para aplicar o Flyway, gera
um arquivo temporário de conexão (removido ao terminar) e executa somente
`hbm2java` do Hibernate. A configuração das tabelas e dos tipos está em
`src/main/hibernate/hibernate-reverse-engineering.xml`. Revise as classes
geradas após cada alteração nas migrations. Em novas tabelas, aplique a
migration e gere a classe **antes** de compilar o adaptador que depende dela;
o script inicia a aplicação e precisa que as fontes atuais compilem. Um
`mvn test` normal **não** regenera fontes nem depende do banco local.

## Testes

Testcontainers inicia seu próprio PostgreSQL temporário e aplica as migrations:

```sh
MAVEN_USER_HOME=/data/SmartFinance/maven-home TMPDIR=/data/SmartFinance/tmp \
  ./mvnw -Dmaven.repo.local=/data/SmartFinance/maven-repository test
```

O Maven Wrapper, as dependências e as imagens Docker ficam no HD. O projeto e
suas classes compiladas (`target/`) continuam na pasta do IntelliJ no SSD.
Se o HD estiver montado em outro lugar, ajuste os caminhos do cache Maven
por variáveis de ambiente.

Para testar e compilar o frontend:

```sh
cd frontend
npm test -- --watch=false
npm run build
```

O build gera `frontend/dist/` no SSD. As páginas são carregadas sob demanda;
o bundle inicial fica em torno de 533 kB, acima do padrão de 500 kB, e o
orçamento local foi elevado para 600 kB. Reduzir dependências do layout
raiz é uma otimização possível para uma próxima fatia.

Validação atual: 84 testes de integração/unidade do backend e 42 testes
do frontend passaram. Também foram percorridos no navegador, pelo proxy
local, cadastro → login → home com menu lateral → criação de categoria →
criação de série pela página de lançamentos (31/01, 28/02, 31/03) →
cancelamento de uma ocorrência → edição de uma ocorrência prevista
(descrição e valor) → edição “esta e as futuras” de uma série (com
reancoragem do dia mensal e preservação da ocorrência cancelada) → cadastro
de cartão de crédito → compra parcelada de R$ 150,00 em duas parcelas (uma
na fatura aberta de dezembro/2026 e outra na projetada de janeiro/2027) →
resumo de dezembro/2026 com a parcela prevista de R$ 75,00 → fechamento da
fatura → pagamento de R$ 75,00 pela carteira (fatura `PAGA`, saldo estimado
da carteira em −R$ 75,00 e parcela migrando de despesa prevista para
realizada no resumo) → compra parcelada de R$ 24,00 em outro cartão →
estorno integral, cancelando ambas as parcelas abertas sem crédito → edição
do nome e desativação/reativação do cartão → seleção “Neutra” e registro de
despesa comum pela página de lançamentos, com exibição da classificação.
Também foram testadas no navegador a desativação e reativação de categoria
e a remoção temporária da categoria inativa do seletor de compra → criação
de uma entidade ("Padaria do Bairro") junto com a despesa, aparecendo na
entidade do lançamento e na lista de entidades → correção do vínculo de
entidade em lançamento efetivado e em compra de cartão, sem alterar
valores. Foi corrigido também um teste dependente do horário do dia: o
pagamento de fatura usava `T12:00:00Z` no dia corrente, que passa a ser
futuro entre 00:00 e 12:00 UTC e derrubava a suíte nesse intervalo; agora
usa `T00:00:00Z` e há um caso explícito para data futura. O resumo mensal
passou a exibir também o agrupamento por categoria e natureza, com parcelas
de cartão no mês da fatura, reconciliado com os totais do mês. O filtro por
entidade passou a valer também para o resumo mensal e surgiu a página de
Relatórios, com receitas e despesas por entidade em um intervalo de meses
(grupo “Sem entidade” para reconciliar com o período). O cadastro de conta
passou a aceitar tipo e instituição, com o catálogo global de instituições
(`GET /api/institutions`) e a entrada `OUTRA` para nomes ainda fora do
catálogo. A página de Relatórios ganhou a série mensal do período
(`GET /api/reports/monthly`), com meses vazios preenchidos com zero e um
gráfico de receitas e despesas realizadas desenhado em SVG — sem biblioteca
de gráficos, porque o frontend apenas apresenta os números calculados no
backend. O cartão também passou a referenciar instituições do catálogo,
com a entrada `OUTRA` para nomes desconhecidos.
O agrupamento mensal foi testado com lançamentos legados, despesas comuns e
parcelas abertas/estornadas de cartão. Estorno de parcelas fechadas,
abatimento em fatura futura, ordem dos créditos e idempotência foram
validados por testes de integração. Além
disso, foi criada/listada uma carteira. Home,
páginas roteadas, resumo mensal e transferências são cobertos por testes de
componente/estado Angular.

**Fechamento da V1.** As migrations rodam do zero a cada execução da suíte
(15 aplicadas em banco limpo) e o catálogo global está íntegro. O isolamento
entre usuários e o CSRF são verificados por testes em cada escrita; rotas sem
sessão respondem `401`. Todas as telas têm estado vazio próprio. Um percurso
completo foi feito no navegador com usuário novo: cadastro → login → home
vazio → categoria → conta → despesa com entidade criada junto → cartão →
compra parcelada de R$ 120,00 → resumo mensal com agrupamentos → filtro por
entidade → relatórios com gráfico e quebra por entidade. O Lighthouse na
página de relatórios acusa acessibilidade 1,0 e boas práticas 1,0 (o SEO
perde um ponto só pela ausência de meta description, irrelevante para um
aplicativo autenticado). O cartão passou a referenciar instituições do
catálogo, com a entrada `OUTRA` para nomes desconhecidos, igual às contas.

Lacunas conhecidas, todas fora do escopo documentado da V1 ou explicitamente
adiadas: edição/exclusão de contas, edição de nome ou natureza de categoria,
edição dos valores de lançamentos já efetivados, conciliação bancária,
pagamento parcial de fatura, devolução parcial, entrada por voz, múltiplas
moedas, contabilidade formal e projeção de saldo futuro por conta. O fluxo
de solicitação de inclusão de instituição no catálogo continua não
definido, e a lista de instituições pré-cadastradas é provisória.

O que foi exercido no navegador criou usuários, carteiras, categorias,
séries, cartões, compras e pagamentos de teste no banco local; isso não
equivale a uma validação de segurança para produção nem a uma aprovação
final da aparência.

## Build de produção e implantação

O jar de produção contém a API e o Angular já compilado:

```sh
./mvnw -Pfrontend -DskipTests package
```

O profile `frontend` roda `npm ci` e `npm run build` antes de empacotar
`frontend/dist/frontend/browser` em `static/`. Sem o profile, o build usa o
`dist/` existente (ou nenhum frontend, se ele não tiver sido gerado). A SPA é
servida com fallback para `index.html` em rotas do Angular; `/api`,
`/actuator`, `/error` e caminhos com extensão nunca caem no shell.
Para executar o jar diretamente, configure antes as variáveis de banco e o
segredo JWT descritos acima em “Banco local (Docker)”; na Oracle, o Compose
faz isso via `deploy/.env`.

Para uma VM da Oracle Cloud Always Free há uma stack Docker Compose com
PostgreSQL 18, aplicação e Caddy (TLS automático):

- `deploy/compose.yaml`: serviços, rede interna, volumes e healthchecks.
- `deploy/Dockerfile`: build multi-stage Node → Maven → JRE sem root.
- `deploy/Caddyfile`: HTTPS e proxy para `app:8080`.
- `deploy/backup.sh`: `pg_dump` com retenção e escrita atômica.
- `deploy/.env.example`: variáveis e segredos a preencher.
- [deploy/ORACLE.md](deploy/ORACLE.md): roteiro de console, firewall, DNS,
  deploy, convites, backup, restauração, atualizações e checklist de go-live.

O banco não publica porta no host, e a aplicação não é acessível fora da rede
interna do Compose. O rate limit e os jobs agendados são por instância; a
topologia assume **uma réplica**. O fuso de negócio padrão é
`America/Sao_Paulo` (`SMARTFINANCE_TIME_ZONE`).
Essa stack foi validada localmente com HTTPS em `localhost` (certificado de
desenvolvimento do Caddy), cadastro, login, backup e restauração. Ainda não
houve implantação na Oracle nem certificado de domínio público.
