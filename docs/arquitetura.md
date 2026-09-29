# Arquitetura inicial

O projeto utiliza um monorepo para manter API, interface e infraestrutura versionadas em conjunto. O backend começa como um monólito modular, adequado ao escopo do produto e fácil de executar localmente.

```mermaid
flowchart LR
    U[Usuário] --> F[Frontend React]
    F -->|HTTP + JWT| B[API Spring Boot]
    B --> A[Autenticação]
    B --> C[Chamados]
    B --> S[SLA e histórico]
    A --> P[(PostgreSQL)]
    C --> P
    S --> P
```

## Perfis

- `SOLICITANTE`: abre chamados e acompanha os próprios atendimentos.
- `TECNICO`: recebe, classifica e atende chamados atribuídos.
- `ADMIN`: administra usuários, categorias e regras gerais.

## Fluxo de autenticação

```mermaid
sequenceDiagram
    participant U as Usuário
    participant F as Frontend
    participant A as API
    participant B as PostgreSQL
    U->>F: Cadastro ou login
    F->>A: Credenciais via HTTPS
    A->>B: Valida usuário e senha BCrypt
    A-->>F: Token JWT assinado
    F->>A: Authorization: Bearer token
    A-->>F: Recurso protegido conforme perfil
```

O cadastro público cria somente solicitantes. A elevação para `TECNICO` ou `ADMIN` será uma operação administrativa protegida.

## Isolamento de dados

- Solicitantes listam e consultam apenas chamados vinculados ao próprio e-mail autenticado no JWT.
- A tentativa de consultar um chamado de outro solicitante retorna `404`, evitando revelar que o registro existe.
- Técnicos e administradores podem consultar a fila geral.
- Somente o solicitante pode editar ou excluir o próprio chamado, e apenas enquanto o status for `ABERTO`.

## Estados iniciais do chamado

```mermaid
stateDiagram-v2
    [*] --> ABERTO
    ABERTO --> EM_TRIAGEM
    EM_TRIAGEM --> EM_ATENDIMENTO
    EM_ATENDIMENTO --> AGUARDANDO_USUARIO
    AGUARDANDO_USUARIO --> EM_ATENDIMENTO
    EM_ATENDIMENTO --> RESOLVIDO
    RESOLVIDO --> FECHADO
    RESOLVIDO --> REABERTO
    REABERTO --> EM_ATENDIMENTO
```
