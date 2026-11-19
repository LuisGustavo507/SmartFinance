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
[backups](https://docs.railway.com/volumes/backups) (o backup gerenciado não
está incluído no Hobby). Confirme os preços atuais no
[painel de uso](https://docs.railway.com/pricing/cost-control).

## Antes de criar os serviços

- Plano Hobby contratado **por você**; ele tem mensalidade mínima e pode
  cobrar uso acima da franquia. Configure alerta e limite rígido de gastos.
  O mínimo atual para o limite rígido de Compute Usage é US$ 10 por ciclo
  (confira no painel); atingir esse limite derruba os serviços. Acompanhe
  o uso real após subir.
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
4. O plano **Hobby não inclui backups nativos** da Railway. Não dependa do
   botão de backup do painel nem habilite Public Access para contornar isso.
   A cópia de segurança será externa, com `pg_dump` pelo túnel privado do
   CLI, conforme a seção [Backup externo](#backup-externo).
   Antes de convidar pessoas reais, faça o primeiro dump e ensaie a
   restauração em um banco local isolado.

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

## Backup externo

O Hobby não oferece backup gerenciado. A estratégia é copiar o banco para
fora da Railway usando o túnel privado do CLI, sem publicar a porta do
PostgreSQL. Isso ainda **não foi validado** contra a Railway; valide logo
depois que o app subir e antes do primeiro convite.

Pré-requisitos no seu computador:

- Railway CLI autenticado (`railway login`) e projeto vinculado
  (`railway link`, ambiente de produção).
- Cliente PostgreSQL com `pg_dump`/`pg_restore` na mesma versão major do
  servidor (18, se for a versão escolhida). O `psql` também é necessário
  para `railway connect`.
- Espaço local e uma cópia externa (disco ou armazenamento em nuvem) para
  guardar os dumps. Não coloque dumps nem credenciais no Git.

Procedimento manual:

1. No terminal A, abra o túnel e mantenha-o aberto:

   ```bash
   railway connect Postgres --ssh --tunnel-only -P 5433
   ```

   O comando imprime os dados de conexão locais. Não cole essa saída no
   chat. Se a porta 5433 estiver ocupada, use outra.

2. No terminal B, com o túnel aberto, carregue a URL impressa sem deixá-la
   no histórico (ou use `.pgpass`) e gere o dump:

   ```bash
   read -r -s -p 'URL do túnel: ' SMARTFINANCE_PG_URL; echo
   DUMP="smartfinance-$(date +%F-%H%M).dump"
   pg_dump "$SMARTFINANCE_PG_URL" \
     --format=custom --no-owner --no-privileges --file="$DUMP"
   pg_restore --list "$DUMP" | head
   ```

3. Confira o tamanho, guarde uma cópia fora do projeto e remova dumps
   antigos conforme sua retenção. Feche o túnel no terminal A com `Ctrl+C`.

4. Ensaie a restauração em um PostgreSQL local isolado, **nunca** no banco
   de produção. A imagem oficial inicializa em duas etapas: o servidor
   temporário da primeira etapa não escuta TCP, mas `docker exec pg_isready`
   pode responder cedo demais. Espere uma conexão TCP real antes do restore;
   caso contrário, o `pg_restore` pode coincidir com o desligamento do
   servidor temporário.

   ```bash
   docker rm -f sf-restore-check 2>/dev/null || true
   docker run -d --name sf-restore-check \
     -e POSTGRES_PASSWORD=local-only -e POSTGRES_DB=smartfinance \
     -p 127.0.0.1:55432:5432 postgres:18-alpine

   ready=0
   for i in $(seq 1 60); do
     if PGPASSWORD=local-only psql -h 127.0.0.1 -p 55432 -U postgres \
       -d smartfinance -c 'select 1' >/dev/null 2>&1; then
       ready=1
       break
     fi
     sleep 1
   done
   if [ "$ready" != 1 ]; then
     docker logs sf-restore-check | tail -40
     exit 1
   fi

   PGPASSWORD=local-only pg_restore --clean --if-exists --no-owner \
     --no-privileges -h 127.0.0.1 -p 55432 -U postgres \
     -d smartfinance "$DUMP"
   PGPASSWORD=local-only psql -h 127.0.0.1 -p 55432 -U postgres \
     -d smartfinance \
     -c 'select version, description, success from flyway_schema_history order by installed_rank desc limit 3;'
   docker rm -f sf-restore-check
   ```

5. Depois de validar, defina a frequência. O Hobby não agenda isso: use
   `cron`/timer no seu computador enquanto ele estiver ligado ou um runner
   externo, sempre pelo túnel privado. Um backup só é útil se a restauração
   também for testada.

## 4. Operação e go-live

- Acompanhe **Deploy Logs** do app, **Metrics** e custo de ambos os serviços.
  Os builds são gratuitos, mas app, banco, volume e tráfego geram uso.
- Healthcheck da Railway confirma a subida de um deploy; **não é monitor
  contínuo**. Configure alertas de falha/custo e confira o health após updates.
- Antes de convidar pessoas reais, faça o primeiro `pg_dump` externo pelo
  túnel privado e ensaie a restauração em banco local isolado, sem
  substituir o de produção. O Hobby **não tem backup automático**; a cópia
  externa é responsabilidade sua. **Não use o botão Restore no banco ativo
  para fazer esse ensaio**, mesmo que ele apareça no painel.
- Para atualizações, preserve o banco e o segredo JWT, rode a suíte local,
  revise migrations, faça backup, publique o commit aprovado e confira
  logs/health/fluxos. Mudança do JWT desloga usuários.
- Não habilite escalonamento horizontal: os jobs e o rate limit não têm
  coordenação entre réplicas.

### Checklist (ainda pendente de validação na Railway)

- [x] Build Docker e testes locais; contêiner com `PORT` dinâmico, banco
  iniciando ao mesmo tempo, TLS encaminhado, SPA e readiness validados.
- [ ] Plano/custos e limite rígido conferidos pelo proprietário da conta.
- [x] PostgreSQL privado e persistente, versão conferida; backup nativo
  indisponível no Hobby.
- [ ] Serviço web único em HTTPS com segredo JWT privado e uma réplica.
- [x] V15 aplicada, readiness `UP`, SPA e API com respostas esperadas.
- [ ] Cookie `Secure`, headers, rate limit e cadastro por convite validados.
- [x] `pg_dump` externo pelo túnel privado e restauração local validados
  (18 tabelas restauradas, Flyway até V15).
- [ ] Primeiros convidados e aprovação visual do usuário antes de abrir o beta.
