# Backup externo do PostgreSQL na Railway

Status: **validado em 2026-11-19** no projeto SmartFinance, plano Hobby.
O banco é PostgreSQL 18, serviço `Postgres`, acessível apenas pela rede
privada. O Hobby não inclui backup gerenciado.

O último dump validado foi gerado pelo script em
`2026-11-19T23:27:57Z` (68 KB) e restaurado em um PostgreSQL local isolado:
18 tabelas, Flyway até a V15 e o usuário de teste presente.

## Por que o backup é externo

A Railway mantém o banco em um volume privado. Sem backup nativo no Hobby,
uma exclusão acidental, corrupção ou perda do volume não teria recuperação.
A cópia externa usa um túnel SSH do Railway CLI e grava um dump lógico no
seu computador, sem publicar a porta do PostgreSQL.

```text
PostgreSQL na Railway (privado)
        ↑ túnel SSH do Railway CLI
127.0.0.1:5433 no seu computador
        ↑ pg_dump
arquivo .dump → cópia criptografada fora da Railway
```

Não habilite **Public Access** nem **TCP Proxy** no banco para fazer backup.

## O que o dump contém

Inclui:

- schema das tabelas, índices, constraints, sequences e extensões;
- dados de todas as tabelas, inclusive `flyway_schema_history`;
- histórico de migrations.

Não inclui:

- usuários/roles e permissões do servidor;
- variáveis de ambiente da Railway, como `SMARTFINANCE_JWT_SECRET`;
- configuração do servidor ou o volume bruto;
- point-in-time recovery: o RPO é o intervalo entre backups.

Guarde o segredo JWT e as demais variáveis críticas em um gerenciador de
senhas. Se o JWT for perdido, os dados continuam recuperáveis, mas as sessões
existentes deixam de valer.

## Instalação no Ubuntu 26.04

O ambiente usado no SmartFinance é Ubuntu 26.04. Instale o cliente
PostgreSQL 18 e o Railway CLI:

```sh
sudo apt update
sudo apt install -y postgresql-client-18

bash <(curl -fsSL railway.com/install.sh)
export PATH="$HOME/.railway/bin:$PATH"
echo 'export PATH="$HOME/.railway/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc

psql --version
pg_dump --version
pg_restore --version
railway --version
```

O script também usa `python3` (para ler as variáveis do serviço sem imprimir
segredos), `setsid` (para isolar o túnel) e `fuser` ou `ss` (para fechar a
porta local). No Ubuntu eles já vêm instalados; se faltar algum:

```sh
sudo apt install -y python3 util-linux psmisc
```

Docker é necessário apenas para o teste de restauração local:

```sh
docker ps
```

Se `docker ps` der erro de permissão:

```sh
sudo usermod -aG docker "$USER"
# encerre a sessão e entre novamente
```

## Configuração inicial

Autentique e vincule o projeto uma única vez:

```sh
cd /home/darl1ngx16/IdeaProjects/SmartFinance
railway login
railway link
railway status
```

Escolha o projeto `SmartFinance` (na conta usada, ele aparece como
`glorious-solace`) e o ambiente `production`. O serviço do banco chama-se
`Postgres`; se o seu nome for diferente, defina `RAILWAY_SERVICE`.

Crie a pasta de backups:

```sh
mkdir -p "$HOME/backups/smartfinance"
chmod 700 "$HOME/backups/smartfinance"
```

## Script

O script fica em [`railway-backup.sh`](railway-backup.sh). Ele:

1. confere se `pg_dump`, `pg_restore`, `psql`, `python3`, `setsid` e o CLI
   existem;
2. verifica se a porta local do túnel está livre;
3. lê `PGUSER`, `PGPASSWORD` e `PGDATABASE` do serviço `Postgres` com
   `railway variable list --json`, sem imprimir os valores;
4. abre `railway connect Postgres --ssh --tunnel-only -P 5433` em segundo
   plano;
5. espera uma conexão TCP real com `psql -c 'select 1'`;
6. executa `pg_dump --format=custom --no-owner --no-privileges`;
7. valida o arquivo com `pg_restore --list`;
8. grava o dump e um arquivo `.sha256` com permissão `600`;
9. remove dumps e checksums mais antigos que a retenção configurada;
10. fecha o túnel e remove arquivos temporários, inclusive em caso de erro.

Uso:

```sh
cd /home/darl1ngx16/IdeaProjects/SmartFinance
./deploy/railway-backup.sh
```

Exemplo de saída:

```text
backup: /home/darl1ngx16/backups/smartfinance/smartfinance-20261119T232757Z.dump
backup: tamanho 68K
backup: sha256 c67f785a2170e088cd29d054d24c0efdddf1fa983920b160380023ba7c79b1bb
```

### Variáveis opcionais

| Variável | Padrão | Uso |
| --- | --- | --- |
| `RAILWAY_BIN` | `railway` ou `~/.railway/bin/railway` | Caminho do CLI |
| `RAILWAY_SERVICE` | `Postgres` | Nome do serviço do banco |
| `RAILWAY_ENVIRONMENT` | ambiente vinculado | Ambiente da Railway |
| `RAILWAY_TUNNEL_PORT` | `5433` | Porta local do túnel |
| `BACKUP_DIR` | `~/backups/smartfinance` | Pasta dos dumps |
| `BACKUP_RETENTION_DAYS` | `30` | Dias de retenção |

Exemplos:

```sh
# Porta alternativa e outro diretório
RAILWAY_TUNNEL_PORT=5434 \
BACKUP_DIR=/mnt/hd/smartfinance \
./deploy/railway-backup.sh

# Serviço com outro nome
RAILWAY_SERVICE=postgres-production ./deploy/railway-backup.sh
```

O script **não** faz upload nem criptografia. A cópia externa é uma etapa
separada, descrita abaixo.

## Validação realizada

O processo manual foi executado primeiro:

1. `railway connect Postgres --ssh --tunnel-only -P 5433` abriu o túnel.
2. `pg_dump` gerou um arquivo custom de 66 KB.
3. A primeira restauração falhou porque o `docker exec pg_isready` respondeu
   durante a inicialização temporária do PostgreSQL; o servidor temporário
   desligou e o `pg_restore` perdeu a conexão. O dump não estava corrompido.
4. A espera foi corrigida para uma conexão TCP real com `psql -c 'select 1'`.
5. A restauração passou: 18 tabelas, Flyway até a V15 com `success = t` e
   dados do usuário de teste.
6. O script foi executado em `2026-11-19T23:27:57Z`, gerou um dump de 68 KB
   e o mesmo teste de restauração passou.

Havia um arquivo de **0 byte** de uma tentativa anterior na pasta de backups.
Ele é inválido e pode ser removido manualmente; o script valida o novo dump
com `pg_restore --list` antes de considerá-lo bom.

## Restauração de teste

Use um banco local isolado. **Nunca** restaure por cima do banco de produção.

```sh
cd "$HOME/backups/smartfinance"
DUMP="$(ls -t smartfinance-*.dump | head -n 1)"
echo "DUMP=$DUMP"
test -s "$DUMP" || { echo "dump ausente ou vazio"; exit 1; }

docker rm -f sf-restore-check 2>/dev/null || true
docker run -d --name sf-restore-check \
  -e POSTGRES_PASSWORD=local-only \
  -e POSTGRES_DB=smartfinance \
  -p 127.0.0.1:55432:5432 \
  postgres:18-alpine

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

PGPASSWORD=local-only pg_restore \
  --clean --if-exists --no-owner --no-privileges \
  -h 127.0.0.1 -p 55432 -U postgres \
  -d smartfinance "$DUMP"

PGPASSWORD=local-only psql \
  -h 127.0.0.1 -p 55432 -U postgres -d smartfinance \
  -c '\dt'

PGPASSWORD=local-only psql \
  -h 127.0.0.1 -p 55432 -U postgres -d smartfinance \
  -c 'select version, description, success from flyway_schema_history order by installed_rank desc limit 5;'

docker rm -f sf-restore-check
```

Resultado esperado: 18 tabelas e a última migration `15 | beta invites | t`.
Se o `pg_restore` falhar, não remova o container antes de ver
`docker logs sf-restore-check | tail -60`.

## Cópia externa e criptografia

O dump contém emails, hashes de senha e dados financeiros. Mantenha
permissão `600` e crie uma cópia fora da máquina.

Exemplo com GPG:

```sh
cd "$HOME/backups/smartfinance"
gpg --symmetric --cipher-algo AES256 smartfinance-*.dump
```

Guarde a passphrase no gerenciador de senhas e copie o `.gpg` para um HD
externo ou armazenamento em nuvem. Sem a passphrase, o arquivo não pode ser
recuperado. Confira a integridade:

```sh
cd "$HOME/backups/smartfinance"
sha256sum -c smartfinance-*.dump.sha256
```

## Agendamento

O Hobby não agenda backup. O script precisa rodar no seu computador ou em
outra máquina sempre ligada, com o CLI autenticado.

Exemplo de `cron` para todos os dias às 03:15:

```cron
15 3 * * * /home/darl1ngx16/IdeaProjects/SmartFinance/deploy/railway-backup.sh >> /home/darl1ngx16/backups/smartfinance/backup.log 2>&1
```

O script encontra `~/.railway/bin/railway` pelo `HOME`; se o cron não
encontrar, defina `RAILWAY_BIN` no início da linha ou em um wrapper. Rode
como seu usuário, não como root: as credenciais do CLI ficam em `~/.railway`.

Alternativa com systemd de usuário:

```ini
# ~/.config/systemd/user/smartfinance-backup.service
[Unit]
Description=Backup externo do SmartFinance

[Service]
Type=oneshot
ExecStart=/home/darl1ngx16/IdeaProjects/SmartFinance/deploy/railway-backup.sh
```

```ini
# ~/.config/systemd/user/smartfinance-backup.timer
[Unit]
Description=Agenda o backup do SmartFinance

[Timer]
OnCalendar=*-*-* 03:15:00
Persistent=true

[Install]
WantedBy=timers.target
```

```sh
systemctl --user daemon-reload
systemctl --user enable --now smartfinance-backup.timer
loginctl enable-linger "$USER"
```

Se o computador estiver desligado no horário, o backup não roda. Para um
beta com dados reais, considere uma máquina sempre ligada ou um runner
externo. Não crie um cron dentro da própria Railway e chame isso de cópia
externa.

## Retenção e RPO

O padrão do script é manter 30 dias de dumps e checksums. Ajuste conforme o
volume e a política do projeto:

```sh
BACKUP_RETENTION_DAYS=90 ./deploy/railway-backup.sh
```

Recomendação inicial:

- um backup antes de cada migration ou deploy relevante;
- backup diário se houver máquina sempre ligada; semanal se não houver;
- pelo menos uma cópia criptografada fora da Railway;
- teste de restauração periódico e após mudanças de schema.

Com backup diário, o RPO é de até 24 horas. O backup manual antes de
migrations reduz o risco nos momentos críticos.

## Segurança

- Não commite dumps, checksums ou arquivos `.gpg` no Git.
- Não cole a saída de `railway variable list`, a URL do túnel ou o JWT no chat.
- Não habilite Public Access/TCP Proxy no banco.
- Não use o botão **Restore** do painel no banco ativo para testar.
- Restrinja a pasta de backups ao seu usuário (`700`) e os arquivos a `600`.
- Remova dumps antigos conforme a retenção; dados pessoais não devem ficar
  indefinidamente.

## Solução de problemas

**`backup: Railway CLI não encontrado`**

Instale o CLI e rode `railway login`. Se ele estiver em outro caminho:

```sh
RAILWAY_BIN="$HOME/.railway/bin/railway" ./deploy/railway-backup.sh
```

**`não foi possível ler as variáveis do serviço`**

Confira `railway status`, o nome do serviço e o ambiente:

```sh
railway status
railway variable list --service Postgres --json >/dev/null && echo ok
```

**`porta local 5433 já está em uso`**

Feche o túnel antigo ou use outra porta:

```sh
RAILWAY_TUNNEL_PORT=5434 ./deploy/railway-backup.sh
```

Se um túnel antigo ficou aberto:

```sh
fuser -k -n tcp 5433
```

**`pg_dump` reclama de versão**

Instale o cliente 18 (`postgresql-client-18`) e confira `pg_dump --version`.
O cliente deve ser da mesma major ou mais novo que o servidor.

**Restauração falha com “server closed the connection unexpectedly”**

A imagem oficial do PostgreSQL inicializa em duas etapas. Use a espera por
TCP com `psql -c 'select 1'` mostrada acima; não confie apenas em
`docker exec pg_isready`.

**Dump de 0 byte**

A execução falhou antes de gravar os dados. Verifique a saída do script, a
conexão com o túnel e o espaço em disco. Um dump de 0 byte não deve ser
mantido como backup válido.
