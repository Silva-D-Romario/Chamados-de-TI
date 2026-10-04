# Chamados de TI

Sistema full stack para abertura, triagem e acompanhamento de chamados de suporte técnico. O projeto foi planejado para demonstrar desenvolvimento backend com regras de negócio, autenticação, testes, documentação de API e infraestrutura reproduzível.

## Objetivos do projeto

- Separar as permissões de solicitantes, técnicos e administradores.
- Registrar todo o histórico de atendimento de um chamado.
- Controlar categoria, prioridade, responsável, status e prazo de SLA.
- Disponibilizar uma API REST documentada com OpenAPI.
- Executar aplicação e banco com Docker.
- Validar backend e frontend automaticamente no GitHub Actions.

## Stack planejada

### Backend

- Java e Spring Boot
- Spring Security e JWT
- Spring Data JPA
- PostgreSQL e Flyway
- Bean Validation
- JUnit, Mockito e Testcontainers
- Springdoc OpenAPI

### Frontend

- React e TypeScript
- Vite
- Interface simples e responsiva

### Infraestrutura

- Docker Compose
- GitHub Actions

## Fluxo Git

- `main`: versão estável.
- `develop`: desenvolvimento e validação.

As mudanças são organizadas em commits pequenos e integradas à `main` por Pull Request.

## Executar o PostgreSQL

```bash
cp .env.example .env
docker compose up -d postgres
```

Antes de usar fora do ambiente local, defina um `JWT_SECRET` forte no arquivo `.env`.

Para criar o primeiro administrador, defina as variáveis antes de iniciar o backend:

```bash
export APP_ADMIN_NAME="Administrador"
export APP_ADMIN_EMAIL="admin@example.com"
export APP_ADMIN_PASSWORD="uma-senha-segura"
```

O administrador é criado apenas quando o e-mail ainda não existe. Depois do primeiro acesso, ele pode promover outros usuários para técnico ou administrador pela interface.
Após uma alteração de perfil, o usuário deve sair e entrar novamente para receber um JWT com a nova permissão.

## Executar o backend

Com o PostgreSQL ativo:

```bash
cd Back
./mvnw spring-boot:run
```

A API responde em `http://localhost:8080`. O endpoint público de verificação é `GET /api/v1/status`.

### Autenticação

| Método | Endpoint | Acesso |
| --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Público |
| `POST` | `/api/v1/auth/login` | Público |
| `GET` | `/api/v1/auth/me` | Token JWT |

Novos cadastros recebem o perfil `SOLICITANTE`. Rotas protegidas usam o cabeçalho `Authorization: Bearer <token>`.

### Chamados

| Método | Endpoint | Regra |
| --- | --- | --- |
| `POST` | `/api/v1/tickets` | Cria um chamado para o usuário autenticado |
| `GET` | `/api/v1/tickets` | Lista somente os próprios chamados; suporte visualiza todos |
| `GET` | `/api/v1/tickets/summary` | Retorna indicadores gerais respeitando usuário e filtros |
| `GET` | `/api/v1/tickets/{id}` | Consulta respeitando a mesma regra de acesso |
| `PUT` | `/api/v1/tickets/{id}` | Solicitante edita o próprio chamado enquanto estiver aberto |
| `DELETE` | `/api/v1/tickets/{id}` | Solicitante exclui o próprio chamado enquanto estiver aberto |
| `PATCH` | `/api/v1/tickets/{id}/assignment` | Administrador atribui um técnico |
| `PATCH` | `/api/v1/tickets/{id}/status` | Técnico responsável ou administrador altera o status |
| `GET` | `/api/v1/tickets/{id}/history` | Retorna o histórico visível ao usuário |
| `POST` | `/api/v1/tickets/{id}/comments` | Adiciona comentário público; suporte também pode criar comentário interno |
| `GET` | `/api/v1/tickets/{id}/comments` | Lista comentários permitidos para o perfil autenticado |
| `POST` | `/api/v1/tickets/{id}/attachments` | Envia um arquivo por formulário multipart |
| `GET` | `/api/v1/tickets/{id}/attachments` | Lista os anexos permitidos ao usuário |
| `GET` | `/api/v1/tickets/{id}/attachments/{attachmentId}` | Faz download autenticado do arquivo |

A listagem é paginada e aceita `page`, `size` e `sort`. Também pode ser filtrada por:

| Parâmetro | Exemplo | Função |
| --- | --- | --- |
| `q` | `notebook` | Busca no título e na descrição |
| `status` | `EM_ATENDIMENTO` | Filtra pela etapa do fluxo |
| `priority` | `ALTA` | Filtra pela prioridade |
| `category` | `Hardware` | Filtra pela categoria |
| `technicianId` | `3` | Filtra pelo técnico responsável |

O endpoint de resumo aceita os mesmos filtros e retorna totais de chamados abertos, em andamento, resolvidos e com SLA vencido.

### Anexos

Chamados aceitam arquivos PDF, PNG, JPG e TXT de até 5 MB. Os metadados ficam no PostgreSQL e os arquivos são gravados no diretório configurado por `ATTACHMENTS_DIR`, que por padrão é `Back/data/attachments` ao iniciar o backend pela pasta `Back`.

Listagem, envio e download seguem a mesma regra de visibilidade do chamado. O nome usado no armazenamento é gerado internamente para impedir colisões e tentativas de escapar do diretório.

### Prazos de SLA

| Prioridade | Prazo inicial |
| --- | --- |
| Crítica | 4 horas |
| Alta | 8 horas |
| Média | 24 horas |
| Baixa | 48 horas |

A resposta do chamado informa `dueAt` e `slaStatus`. Os estados possíveis são `NO_PRAZO`, `EM_RISCO`, `VENCIDO` e `CONCLUIDO`; o risco começa quando resta até 25% do prazo.

### Alertas de SLA

O backend verifica periodicamente os chamados não concluídos. Ao entrar em risco ou vencer, cria uma notificação persistente e sem duplicação para o solicitante e, quando houver atribuição, para o técnico responsável.

| Método | Endpoint | Regra |
| --- | --- | --- |
| `GET` | `/api/v1/notifications` | Lista apenas as notificações do usuário autenticado |
| `GET` | `/api/v1/notifications/unread-count` | Retorna a quantidade ainda não lida |
| `PATCH` | `/api/v1/notifications/{id}/read` | Marca uma notificação própria como lida |
| `PATCH` | `/api/v1/notifications/read-all` | Marca todas as notificações próprias como lidas |

O intervalo padrão é de 60 segundos e pode ser alterado com `SLA_ALERT_INTERVAL_MS`. A interface atualiza os alertas automaticamente no mesmo intervalo.

### Swagger/OpenAPI

Com o backend ativo:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Especificação OpenAPI: `http://localhost:8080/v3/api-docs`

Para testar rotas protegidas na interface, use **Authorize** e informe o token JWT obtido no login.

### Administração de usuários

| Método | Endpoint | Regra |
| --- | --- | --- |
| `GET` | `/api/v1/users` | Lista usuários para administradores |
| `PATCH` | `/api/v1/users/{id}/role` | Altera o perfil do usuário |
| `GET` | `/api/v1/users/technicians` | Lista técnicos ativos para a equipe de suporte |

### Categorias

| Método | Endpoint | Regra |
| --- | --- | --- |
| `GET` | `/api/v1/categories` | Lista categorias ativas para usuários autenticados |
| `GET` | `/api/v1/categories/admin` | Lista todas as categorias para administradores |
| `POST` | `/api/v1/categories` | Administrador cria uma categoria |
| `PATCH` | `/api/v1/categories/{id}` | Administrador renomeia, ativa ou desativa uma categoria |

Novos chamados aceitam somente categorias ativas do catálogo. Categorias são desativadas em vez de excluídas para preservar o histórico dos chamados existentes.

## Executar o frontend

Em outro terminal:

```bash
cd Front
npm install
npm run dev
```

A interface fica disponível em `http://localhost:5173`.

## Executar as validações

```bash
cd Back && ./mvnw verify
cd ../Front && npm run lint && npm run build
```

O backend mantém testes rápidos com H2 e um teste de integração com PostgreSQL 16 via Testcontainers. Para executar a suíte completa localmente, o Docker precisa estar ativo. O teste sobe um banco temporário, aplica todas as migrations Flyway, valida o mapeamento JPA e persiste relacionamentos reais.

## Estrutura

```text
Back/              API Spring Boot
Front/             Aplicação React
docs/              Arquitetura e documentação complementar
.github/workflows/ Integração contínua
compose.yaml       Serviços locais
```

## Roadmap inicial

- [x] Cadastro, login e autenticação JWT.
- [x] Perfis de usuário: solicitante, técnico e administrador.
- [x] Administração, consulta e promoção de usuários.
- [x] Abertura, consulta, edição e exclusão de chamados.
- [x] Isolamento dos chamados por solicitante.
- [x] Atribuição de técnicos e transições de status.
- [x] Histórico auditável do atendimento.
- [x] Comentários públicos e internos entre solicitante e suporte.
- [x] Regras e indicadores de SLA por prioridade.
- [x] Dashboard, busca, filtros e paginação integrada.
- [x] Catálogo de categorias administrável.
- [x] Pipeline inicial de CI para backend e frontend.
- [x] Testes de integração e documentação OpenAPI inicial.
- [x] Anexos protegidos nos chamados.
- [x] Alertas automáticos de SLA e notificações.
- [x] Testes de integração com PostgreSQL real.
- [ ] Deploy público com dados de demonstração.
