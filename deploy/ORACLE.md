# Implantação na Oracle Cloud Always Free

Roteiro para colocar o SmartFinance no ar em uma única VM da Oracle Cloud,
com PostgreSQL, HTTPS automático e backup diário. A conta, a criação da VM e
o DNS são feitos por você no console; este repositório entrega os artefatos.

## Topologia

```
Internet -> Caddy (80/443, TLS automático) -> app Spring Boot (8080, rede interna)
                                                        |
                                                        v
                                             PostgreSQL 18 (volume Docker)
```

- O banco não publica porta no host.
- A aplicação roda como usuário sem privilégios e serve a API e o Angular no
  mesmo domínio, sem CORS.
- O proxy só inicia depois de a aplicação passar no healthcheck.
- Flyway aplica as migrations automaticamente na subida da aplicação.
- O rate limit é em memória: **uma réplica**. Não escale horizontalmente sem
  trocar esse mecanismo.

## Pré-requisitos

- Conta Oracle Cloud com Always Free habilitado.
- Um domínio que você controla (por exemplo, `financeiro.seudominio.com.br`).
- Chave SSH pública para acessar a VM.
- Repositório clonável na VM (público ou com deploy key).

## 1. Rede e firewall da Oracle

1. No console, crie uma VCN com sub-rede pública (assistente "VCN com acesso à
   internet" já resolve).
2. Em **Security Lists** da sub-rede, adicione ingress (restrinja ou remova
   qualquer regra SSH ampla que já venha na VCN):
   - TCP 22 apenas para o seu IP (`SEU_IP/32`).
   - TCP 80 para `0.0.0.0/0` (desafio HTTP do Caddy e redirecionamento).
   - TCP 443 para `0.0.0.0/0`.
   - Opcional: UDP 443 para HTTP/3 (sem isso o Caddy usa HTTP/2 sobre TCP).
3. Mantenha o egress padrão (todo o tráfego).

## 2. Instância

Recomendado (cabe no Always Free):

- Shape `VM.Standard.A1.Flex` (ARM), 2 OCPU e 12 GB de RAM.
- Imagem Ubuntu 24.04.
- IP público IPv4 atribuído.
- Volume de boot de 100 GB (o limite gratuito é 200 GB no total).
- Sua chave SSH cadastrada.

> A1 costuma ter falta de capacidade em algumas regiões. Se não houver vaga,
> tente outra availability domain ou horário. Em último caso, uma instância
> AMD micro (1 GB) funciona, mas precisa de swap e o build demora muito mais.

O build da imagem compila Angular e Maven; menos de 2 GB de RAM costuma
falhar por falta de memória.

## 3. DNS

Crie um registro **A** `financeiro.seudominio.com.br` apontando para o IP
público da VM. Se usar Cloudflare, deixe o proxy **desligado** (nuvem cinza)
para o Caddy emitir o certificado por HTTP-01.

Confirme antes de subir:

```bash
dig +short financeiro.seudominio.com.br
```

## 4. Firewall dentro da VM

As imagens Ubuntu da Oracle bloqueiam portas por iptables, independentemente
do Security List. Libere 80 e 443:

```bash
sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT
sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y iptables-persistent
sudo netfilter-persistent save
```

Confira com `sudo iptables -L INPUT -n --line-numbers`.

## 5. Docker

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"
```

Saia e entre de novo na sessão SSH para o grupo `docker` valer.

## 6. Código e configuração

```bash
sudo mkdir -p /opt/smartfinance
sudo chown "$USER":"$USER" /opt/smartfinance
git clone <URL_DO_REPOSITORIO> /opt/smartfinance
cd /opt/smartfinance/deploy
cp .env.example .env
chmod 600 .env
```

Edite `.env`:

- `SMARTFINANCE_DOMAIN`: o domínio do passo 3.
- `POSTGRES_PASSWORD`: `openssl rand -base64 32`.
- `SMARTFINANCE_JWT_SECRET`: `openssl rand -base64 48`.

Não reutilize o segredo de desenvolvimento: trocar o JWT secret invalida todas
as sessões existentes, o que é aceitável apenas na primeira subida.

## 7. Subir a stack

```bash
cd /opt/smartfinance/deploy
docker compose up -d --build
docker compose ps
docker compose logs -f app
```

A primeira construção instala Node, dependências e Maven; pode levar de 5 a 15
minutos. O Caddy emite o certificado assim que o domínio resolver para a VM.

Verificação:

```bash
curl -fsS https://financeiro.seudominio.com.br/actuator/health
curl -sI https://financeiro.seudominio.com.br/ | grep -i strict-transport-security
curl -sI https://financeiro.seudominio.com.br/carteiras | head -1
```

Esperado: health `UP`, HSTS presente e `200` na rota da SPA. Abra o site no
navegador e faça login.

## 8. Liberar os primeiros convidados

O beta é fechado por lista de emails. Cadastre cada pessoa antes de enviar o
link:

```bash
cd /opt/smartfinance/deploy
docker compose exec -T db psql -U smartfinance -d smartfinance -c \
  "INSERT INTO convite_beta (email, criado_em) VALUES (lower('pessoa@exemplo.com'), now()) ON CONFLICT (email) DO NOTHING;"
```

Conferir pendentes:

```bash
docker compose exec -T db psql -U smartfinance -d smartfinance -c \
  "SELECT email FROM convite_beta WHERE usado_em IS NULL ORDER BY email;"
```

O convite é consumido no cadastro. Para reenviar a alguém que já usou, apague a
linha ou redefina `usado_em`/`usado_por` com cuidado.

## 9. Backup diário

Instale o timer do systemd (roda às 03:15 de Brasília, com retenção de 14
dias):

```bash
cd /opt/smartfinance/deploy
chmod +x backup.sh
sudo cp smartfinance-backup.service smartfinance-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now smartfinance-backup.timer
sudo systemctl list-timers | grep smartfinance
```

Teste imediatamente e confira o arquivo:

```bash
sudo /opt/smartfinance/deploy/backup.sh
sudo ls -lh /var/backups/smartfinance
```

O backup fica no mesmo disco da VM. Copie periodicamente para fora (por
exemplo, `scp` para sua máquina) para proteger contra perda da instância.

### Restaurar um backup

```bash
cd /opt/smartfinance/deploy
docker compose stop app
docker compose exec -T db psql -U smartfinance -d postgres -c \
  "DROP DATABASE smartfinance;"
docker compose exec -T db psql -U smartfinance -d postgres -c \
  "CREATE DATABASE smartfinance OWNER smartfinance;"
docker compose exec -T db pg_restore -U smartfinance -d smartfinance --no-owner \
  < /var/backups/smartfinance/ARQUIVO.dump
docker compose start app
```

## 10. Atualizações

```bash
cd /opt/smartfinance/deploy
sudo ./backup.sh
cd /opt/smartfinance
git pull
cd deploy
docker compose up -d --build
docker compose ps
```

As migrations rodam na subida. Se algo falhar, os logs da aplicação mostram o
erro do Flyway antes de a porta abrir.

## Operação

```bash
cd /opt/smartfinance/deploy
docker compose logs -f app       # aplicação e auditoria
docker compose logs -f caddy     # certificados e requisições
docker compose restart app
docker compose down              # para tudo (mantém os volumes)
```

A auditoria de segurança usa `event=` nas linhas de log (`login_failed`,
`rate_limited`, `registration_rejected`, `user_registered`, ...). O
`X-Request-Id` aparece em cada linha e na resposta de erro.

## Problemas comuns

| Sintoma | Causa provável |
| --- | --- |
| Caddy não emite certificado | DNS ainda não aponta para a VM, portas 80/443 fechadas no Security List ou no iptables, ou proxy Cloudflare ligado. |
| `502 Bad Gateway` | Aplicação ainda subindo ou unhealthy: veja `docker compose logs app`. |
| Login não persiste | Acesso por `http://IP` em vez do domínio HTTPS: o cookie é `Secure`. |
| `403 invite_required` | Email não está em `convite_beta` ou já foi usado. |
| Build morre por memória | Use shape A1 com pelo menos 12 GB; em AMD micro adicione swap. |
| `port is already allocated` | Outro serviço usando 80/443 na VM. |

## Checklist de go-live

- [ ] Security List com 22 restrito, 80 e 443 abertos.
- [ ] iptables persistido com 80/443.
- [ ] DNS A apontando para o IP e certificado válido.
- [ ] `GET /actuator/health` retorna `UP` em HTTPS.
- [ ] `/` e `/carteiras` carregam a SPA; `/api/...` sem sessão retorna 401.
- [ ] `deploy/.env` com permissão `600` e segredos únicos.
- [ ] Primeiro convite consumido em um cadastro de teste.
- [ ] `backup.sh` executado e restauração ensaiada.
- [ ] `smartfinance-backup.timer` ativo.
- [ ] Nenhuma porta do PostgreSQL publicada no host.

## Limitações conhecidas desta versão

- Sem verificação de email: quem se cadastrar primeiro com um email convidado
  fica com a conta. O beta pequeno, o rate limit e a auditoria mitigam.
- Rate limit e jobs agendados são por instância; não há coordenação entre
  réplicas.
- Backup local não substitui cópia off-site.
