# Refatoração incremental de UI/UX

Branch de trabalho: `feat/ui-ux-incremental`. Cada incremento é validado, commitado e enviado separadamente. Não alterar cálculos financeiros nem contratos de API para resolver problemas de apresentação.

| Etapa | Estado | Evidência e commit |
| --- | --- | --- |
| Formulários e áreas de ações | Concluída | 67 testes; build; edição de categoria e alinhamento inspecionados em 390, 768 e 1233 px; edição real salva. `2c62907` |
| Navegação mensal | Concluída | 69 testes; build; quatro páginas inspecionadas em 390 px, sem overflow; seletor direto e virada de ano testados. `f978d61` |
| Total e lista de Faturas | Concluída | 69 testes; build; lista longa e total da API testados; inspeção visual desktop/mobile com 20 parcelas simuladas, histórico separado. `362fb6c` |
| Vencimentos de Contas a pagar | Concluída | 69 testes; build; lista longa simulada em desktop/mobile, filtro e detalhe fora da rolagem. `e44f8b7` |
| Modal compartilhado e Categorias | Concluída | 70 testes; build; edição real, desativação/reativação, confirmação de exclusão recusada, descarte recusado/aceito, foco e visual em desktop/390 px. `0c54f02` |
| Entidades em modal | Concluída | 71 testes; build; renomeação real, desativação/reativação, confirmação de exclusão recusada e inspeção visual em desktop/390 px, sem overflow. Commit desta etapa. |
| Detalhes das parcelas em modal | Pendente | Preservar estorno da compra inteira, entidade e histórico. |
| Revisão cruzada | Pendente | Rever listagens de Lançamentos, Transferências, Relatórios e Cartões; testar responsividade, foco e feedback. |

As capturas de inspeção foram feitas em ambiente local com banco temporário descartável. Listas longas de Faturas e Contas a pagar usaram dados simulados **apenas para avaliar o layout**, não para validar persistência ou cálculos. O build emite aviso de orçamento do bundle inicial (cerca de 653 kB ante 600 kB), sem falhar.

Próximo passo: detalhes das parcelas de Faturas. Registrar aqui resultados, pendências e hash de cada incremento antes de avançar.
