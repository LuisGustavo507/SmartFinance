# Frontend Smart Finance

App Angular 21 + PrimeNG organizado em páginas com rotas reais e layout
mobile-first: home em `/`, carteiras, lançamentos (realizados, previstos,
séries mensais finitas, classificação de necessidade das despesas e edição de
previsões), cartões de crédito (cadastro,
compra parcelada, faturas do mês, pagamento, estorno integral e edição de cartão),
transferências, categorias,
entidades e relatórios (por entidade e período). O cadastro de conta aceita
tipo e instituição, com o catálogo global e a opção de informar uma
instituição fora dele. O cartão usa o mesmo catálogo de instituições. A
página de relatórios mostra a série mensal do período com um gráfico em SVG
(receitas e despesas realizadas) e a quebra por entidade. No desktop
há menu lateral fixo; abaixo de 900 px ele vira uma gaveta acessível por
botão. A paleta é azul claro `#E9F1FA`, azul brilhante `#00ABE4` e branco
`#FFFFFF`; tons mais escuros são usados em textos e botões para manter
contraste. A interface continua evolutiva e ainda não representa a aprovação
visual final.

Novas despesas comuns exigem Necessária, Neutra ou Fútil; receitas não usam
classificação. Despesas anteriores à mudança aparecem como “Não classificada”
e podem permanecer assim ao editar uma previsão antiga.
O resumo mensal apresenta despesas realizadas e previstas por necessidade,
incluindo parcelas de cartão, sem descontar créditos de estorno, e o
agrupamento por categoria e natureza.
Categorias podem ser renomeadas; sem vínculos, a natureza pode ser trocada
e a categoria excluída. Categorias também podem ser desativadas e reativadas sem perder o histórico;
categorias inativas deixam os seletores de novos registros.
No lançamento e na compra no cartão há um campo para cadastrar a entidade
junto com o registro, sem sair da tela. A ação "Entidade" corrige o vínculo
de um lançamento ou de uma compra já registrada, sem alterar valores.
O registro de compras agora fica no formulário de Lançamentos: selecionando
“Cartão de crédito” surgem cartão e parcelas; Cartões concentra cadastro,
configuração e faturas. Valores monetários são digitados pelos centavos
(`4599` → `R$ 45,99`) e datas são exibidas em português brasileiro.
Descrições são opcionais em lançamentos e compras.

O estado compartilhado e as chamadas à API ficam em `src/app/finance-store.ts`;
as páginas ficam em `src/app/pages/` e são carregadas sob demanda pelas rotas
em `src/app/app.routes.ts`. Guards em `auth.guard.ts` aguardam a restauração
da sessão: rotas internas sem sessão redirecionam para `/login`.

Instalação, execução, testes, proxy, segurança e localização dos caches estão
documentados no [`README.md` principal](../README.md).
