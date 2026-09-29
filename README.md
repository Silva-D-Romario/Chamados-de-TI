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
| `GET` | `/api/v1/tickets/{id}` | Consulta respeitando a mesma regra de acesso |
| `PUT` | `/api/v1/tickets/{id}` | Solicitante edita o próprio chamado enquanto estiver aberto |
| `DELETE` | `/api/v1/tickets/{id}` | Solicitante exclui o próprio chamado enquanto estiver aberto |

A listagem é paginada e aceita os parâmetros `page`, `size` e `sort`.

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
- [ ] Administração e consulta de usuários.
- [x] Abertura, consulta, edição e exclusão de chamados.
- [x] Isolamento dos chamados por solicitante.
- [ ] Atribuição de técnicos e transições de status.
- [ ] Comentários e histórico de alterações.
- [ ] Regras e indicadores de SLA.
- [ ] Dashboard completo e filtros; paginação inicial disponível na API.
- [x] Pipeline inicial de CI para backend e frontend.
- [ ] Ampliar testes e adicionar documentação OpenAPI.
