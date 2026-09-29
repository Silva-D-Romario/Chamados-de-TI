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

## Estrutura

```text
Back/              API Spring Boot
Front/             Aplicação React
docs/              Arquitetura e documentação complementar
.github/workflows/ Integração contínua
compose.yaml       Serviços locais
```

## Roadmap inicial

- [ ] Autenticação e autorização por perfil.
- [ ] Cadastro e consulta de usuários.
- [ ] Abertura e acompanhamento de chamados.
- [ ] Atribuição de técnicos e transições de status.
- [ ] Comentários e histórico de alterações.
- [ ] Regras e indicadores de SLA.
- [ ] Dashboard, filtros e paginação.
- [ ] Testes, documentação OpenAPI e CI.

