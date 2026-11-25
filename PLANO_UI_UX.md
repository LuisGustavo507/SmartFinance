# Refatoração incremental de UI/UX

Branch de trabalho: `feat/ui-ux-incremental`. Cada incremento é validado, commitado e enviado separadamente. Não alterar cálculos financeiros nem contratos de API para resolver problemas de apresentação.

| Etapa | Estado | Evidência e commit |
| --- | --- | --- |
| Formulários e áreas de ações | Concluída | 67 testes; build; edição de categoria e alinhamento inspecionados em 390, 768 e 1233 px; edição real salva. `2c62907` |
| Navegação mensal | Concluída | 69 testes; build; quatro páginas inspecionadas em 390 px, sem overflow; seletor direto e virada de ano testados. `f978d61` |
| Total e lista de Faturas | Concluída | 69 testes; build; lista longa e total da API testados; inspeção visual desktop/mobile com 20 parcelas simuladas, histórico separado. `362fb6c` |
| Vencimentos de Contas a pagar | Concluída | 69 testes; build; lista longa simulada em desktop/mobile, filtro e detalhe fora da rolagem. `e44f8b7` |
| Modal compartilhado e Categorias | Concluída | 70 testes; build; edição real, desativação/reativação, confirmação de exclusão recusada, descarte recusado/aceito, foco e visual em desktop/390 px. `0c54f02` |
| Entidades em modal | Concluída | 71 testes; build; renomeação real, desativação/reativação, confirmação de exclusão recusada e inspeção visual em desktop/390 px, sem overflow. `def6f82` |
| Detalhes das parcelas em modal | Concluída | 73 testes; build; compra real em 3 parcelas, entidade propagada e estorno das três; entidade inativa rotulada no modal; foco devolvido à lista de histórico; modal e lista em 390 px, sem overflow. `1554396` |
| Revisão cruzada | Concluída | 75 testes; build; vínculo de entidade em Lançamentos e manutenção de Cartões movidos para modal com confirmação de descarte; desativação/reativação real de cartão e descarte real de vínculo testados. Lançamentos, Transferências, Relatórios (4 meses/3 entidades) e Cartões inspecionados em 390 px sem overflow. `0d1326b` |

As capturas de inspeção foram feitas em ambiente local com banco temporário descartável. Listas longas de Faturas e Contas a pagar usaram dados simulados **apenas para avaliar o layout**, não para validar persistência ou cálculos. O build emite aviso de orçamento do bundle inicial (cerca de 653 kB ante 600 kB), sem falhar.

Revisão incremental encerrada. O aviso de orçamento do bundle inicial permanece, sem falha de build.
