# Implantação na Railway — beta fechado

Este roteiro **não executa a implantação**. Só prossiga depois de autorizar a
publicação e revisar o custo no painel. A arquitetura é um serviço web
(Spring Boot + Angular no mesmo jar) e um serviço PostgreSQL da Railway. A
Railway termina o HTTPS e fornece o domínio; **não** importe
`deploy/compose.yaml` nem crie serviços Caddy ou PostgreSQL a partir de uma
imagem Docker. Esses arquivos são da implantação antiga em VM.

Fontes: [Dockerfile personalizado](https://docs.railway.com/builds/dockerfiles),
[PostgreSQL](https://docs.railway.com/databases/postgresql),
[variáveis de referência](https://docs.railway.com/variables#referencing-another-services-variable),
[healthchecks](https://docs.railway.com/deployments/healthchecks) e
[backups](https://docs.railway.com/volumes/backups). Confirme os preços atuais
no [painel de uso](https://docs.railway.com/pricing/cost-control).

## Antes de criar os serviços

- Plano Hobby contratado **por você**; ele tem mensalidade mínima e pode
  cobrar uso acima da franquia. Configure alerta e limite rígido de gastos.
  Atingir o limite derruba os serviços; acompanhe o uso real após subir.
- Repositório GitHub `LuisGustavo507/SmartFinance` atualizado com esta
  preparação. **Conectar um repositório pode iniciar um deploy automaticamente**:
  só faça isso quando concordar em publicar.
- Um único ambiente de produção e **uma réplica** do app. Rate limit e jobs
  agendados ficam em memória/por instância.
- Segredo JWT novo, com pelo menos 32 bytes de entropia. Gere localmente com
  `openssl rand -base64 48`; **não cole o valor em chat nem no Git**.

## 1. Projeto e banco (no painel Railway)

1. Crie um projeto vazio e um serviço **Database → PostgreSQL**, chamado
   `Postgres` (os exemplos de variáveis abaixo usam esse nome exato).
2. Confira a versão major do PostgreSQL; a suíte local usa 18. O Flyway
   aplica as 15 migrations automaticamente quando o app iniciar. Não monte
   nem publique portas do banco manualmente: o serviço já tem armazenamento
   persistente e rede privada dentro do projeto.
3. Espere o banco ficar saudável antes de criar o serviço web. Não crie
   tabelas à mão e não importe o banco local de testes.
4. Ative **Backups → Daily** no serviço do banco e faça um backup manual
   antes de futuras migrações. Backup da Railway tem retenção limitada e
   permanece no mesmo projeto; planeje uma cópia externa antes de convidar
   pessoas reais. [Política de backup](https://docs.railway.com/volumes/backups).

## 2. Serviço web (no painel Railway)

1. Adicione **GitHub Repo → SmartFinance**, branch `main`. Deixe **Root
   Directory** vazio/raiz do repositório para o Dockerfile acessar `frontend/`,
   `src/`, `.mvn/` e `pom.xml`.
2. Em **Build**, escolha Dockerfile e informe `deploy/Dockerfile`; se o
   painel solicitar caminho absoluto ou a detecção falhar, use a variável
   `RAILWAY_DOCKERFILE_PATH=/deploy/Dockerfile`. Não use Railpack/Nixpacks.
3. Em **Variables**, crie as variáveis abaixo. Use o autocomplete do painel
   para as referências ao serviço `Postgres`; elas não copiam a senha para o
   repositório. **Não** configure um `DATABASE_URL` puro: Spring espera JDBC.

   | Variável do serviço web | Valor |
   | --- | --- |
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
   | `SPRING_DATASOURCE_USERNAME` | `${{Postgres.PGUSER}}` |
   | `SPRING_DATASOURCE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
   | `SMARTFINANCE_JWT_SECRET` | segredo gerado localmente; marque como sealed se possível |
   | `SMARTFINANCE_COOKIE_SECURE` | `true` |
   | `SMARTFINANCE_INVITE_REQUIRED` | `true` |
   | `SMARTFINANCE_TIME_ZONE` | `America/Sao_Paulo` |
   | `SMARTFINANCE_TRUST_REAL_IP` | `true` |
   | `SMARTFINANCE_TRUST_FORWARDED_FOR` | `false` |
   | `SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT` | `60000` (aguarda até 60 s pelo banco ao iniciar) |
   | `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | `4` |
   | `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE` | `1` |

   `X-Real-IP` é o cabeçalho [documentado pela Railway para o IP do
   cliente](https://docs.railway.com/networking/public-networking/specs-and-limits).
   O rate limit **não** usa o primeiro `X-Forwarded-For`, que pode vir do
   cliente e ser forjado. Essa configuração só é segura se o app for exposto
   exclusivamente pelo proxy da Railway. Verifique o comportamento no
   domínio real antes de convidar usuários.

   Não é necessário definir `PORT`: a Railway o injeta e o Spring Boot
   já escuta `${PORT:8080}`. O horário do contêiner pode ficar em UTC: as
   regras de calendário usam `SMARTFINANCE_TIME_ZONE`.
4. Em **Settings → Healthcheck**, configure
   `/actuator/health/readiness` (público, sem detalhes), timeout padrão de
   300 segundos. Em **Networking**, gere um domínio `*.up.railway.app`;
   o HTTPS é automático. **Não crie um segundo serviço para o Angular.**
5. Confira se o serviço subiu com **uma réplica**, migrations V1–V15
   aplicadas, health `UP` e nenhum segredo nos logs. O Railway não tem
   `depends_on`; a configuração Hikari acima tolera uma inicialização curta
   do banco. Se o banco ficar indisponível por mais de 60 segundos, aguarde
   a saúde dele e redeploy o app. Não exponha a porta do banco para
   contornar isso.

> A Railway não aceita novos serviços com `railway.json`/`railway.toml`:
> **Config as Code foi descontinuado**. Por ora, configure estes campos no
> painel; caso se justifique depois, migre para `.railway/railway.ts` (IaC)
> com revisão explícita do plano antes de aplicar.

## 3. Validação antes dos convites

No domínio gerado (substitua a URL):

```bash
BASE=https://seu-app.up.railway.app
curl -fsS "$BASE/actuator/health/readiness"
curl -sI "$BASE/" | grep -Ei 'HTTP/|strict-transport-security'
curl -sI "$BASE/carteiras" | head -1
curl -si "$BASE/api/wallets" | head -1
```

Esperado: health `UP`, `/` e `/carteiras` com `200`, API sem sessão
com `401` JSON, cookies `Secure` depois do CSRF/login. Faça um cadastro com
email não convidado (`403 invite_required`) e, após inserir um email de
teste na lista, cadastro, login e consulta de carteira. Teste também no
navegador real, inclusive recarregar a rota `/carteiras`. Confirme que o
rate limit não é contornado enviando diferentes `X-Forwarded-For`; se a
Railway não fornecer `X-Real-IP` como previsto, **não convide usuários**:
investigue o proxy primeiro.

### Inserir um convite sem publicar o banco

Com o CLI Railway autenticado e ligado ao projeto e `psql` instalado,
`railway connect Postgres --ssh` abre um túnel privado. No prompt `psql`:

```sql
INSERT INTO convite_beta (email, criado_em)
VALUES (lower('seu-email-de-teste@exemplo.com'), now())
ON CONFLICT (email) DO NOTHING;
```

O consumo do convite é atômico com o cadastro; não reutilize emails que já
tenham `usado_em`. Não habilite **Public Access / TCP Proxy** no banco só
para administrar convites.

## 4. Operação e go-live

- Acompanhe **Deploy Logs** do app, **Metrics** e custo de ambos os serviços.
  Os builds são gratuitos, mas app, banco, volume e tráfego geram uso.
- Healthcheck da Railway confirma a subida de um deploy; **não é monitor
  contínuo**. Configure alertas de falha/custo e confira o health após updates.
- Antes de convidar pessoas reais, confirme backup automático, faça um
  backup manual e ensaie a restauração de um `pg_dump` em banco local
  isolado, sem substituir o de produção. Backups nativos da Railway só
  podem ser restaurados no mesmo projeto e ambiente: **não use o botão
  Restore no banco ativo para fazer esse ensaio**. Defina uma cópia externa
  de longo prazo usando o túnel privado do CLI.
- Para atualizações, preserve o banco e o segredo JWT, rode a suíte local,
  revise migrations, faça backup, publique o commit aprovado e confira
  logs/health/fluxos. Mudança do JWT desloga usuários.
- Não habilite escalonamento horizontal: os jobs e o rate limit não têm
  coordenação entre réplicas.

### Checklist (ainda pendente de validação na Railway)

- [x] Build Docker e testes locais; contêiner com `PORT` dinâmico, banco
  iniciando ao mesmo tempo, TLS encaminhado, SPA e readiness validados.
- [ ] Plano/custos e limite rígido conferidos pelo proprietário da conta.
- [ ] PostgreSQL privado, persistente, versão e backup conferidos.
- [ ] Serviço web único em HTTPS com segredo JWT privado e uma réplica.
- [ ] V15 aplicada, readiness `UP`, SPA e API com respostas esperadas.
- [ ] Cookie `Secure`, headers, rate limit e cadastro por convite validados.
- [ ] Backup externo testado e restauração ensaiada localmente sem tocar na produção.
- [ ] Primeiros convidados e aprovação visual do usuário antes de abrir o beta.
