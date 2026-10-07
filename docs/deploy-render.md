# Deploy gratuito com Render e Neon

Esta configuração usa o Render para executar a imagem Docker e o Neon para manter o PostgreSQL. O banco externo evita a expiração de 30 dias do PostgreSQL gratuito do Render.

## Responsabilidade de cada serviço

- **Neon:** hospeda o PostgreSQL persistente. Usuários, chamados, categorias, comentários, notificações e históricos continuam armazenados mesmo quando a aplicação reinicia.
- **Render:** constrói o `Dockerfile`, executa o frontend e a API Spring Boot, mantém as variáveis de ambiente e fornece a URL pública. No plano gratuito, o serviço pode hibernar quando fica sem acessos.
- **GitHub:** mantém o código e dispara novos deploys da branch `main` depois que os checks passam.

## 1. Criar o banco no Neon

1. Crie um projeto PostgreSQL gratuito no Neon.
2. Na tela de conexão, selecione Java e copie os dados da conexão.
3. Guarde separadamente:
   - `DB_URL`: URL JDBC no formato `jdbc:postgresql://host/database?sslmode=require`;
   - `DB_USER`: usuário do banco;
   - `DB_PASSWORD`: senha do banco.

O Flyway cria e atualiza as tabelas automaticamente no primeiro deploy.

## 2. Criar o serviço no Render

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/Silva-D-Romario/Chamados-de-TI)

Ao criar o Blueprint, informe os três dados do Neon e uma senha de pelo menos oito caracteres em `APP_DEMO_PASSWORD`. O `JWT_SECRET` é gerado automaticamente pelo Render.

O serviço usa o plano gratuito, a branch `main`, o `Dockerfile` da raiz e o endpoint `/actuator/health`. Novos commits são publicados somente depois que os checks do GitHub passam.

## 3. Criar o primeiro administrador

O cadastro público cria somente usuários `SOLICITANTE`. Para criar o primeiro administrador no Render:

1. Abra o serviço publicado e acesse **Environment**.
2. Clique em **Edit** e depois em **Add variable** três vezes.
3. Adicione as variáveis abaixo, usando uma coluna para a chave e outra para o valor:

   | Chave | Valor de exemplo |
   | --- | --- |
   | `APP_ADMIN_NAME` | `Administrador` |
   | `APP_ADMIN_EMAIL` | `admin@example.com` |
   | `APP_ADMIN_PASSWORD` | uma senha forte com pelo menos 8 caracteres |

4. Use um e-mail que ainda não esteja cadastrado.
5. Clique em **Save, rebuild, and deploy**.
6. Depois do deploy, entre com o administrador.

O administrador pode abrir **Equipe e permissões** e promover um usuário cadastrado para `TECNICO` ou `ADMIN`. O usuário alterado precisa sair e entrar novamente para receber um token com a nova permissão.

Quem apenas clonar o repositório também pode criar o administrador preenchendo `APP_ADMIN_NAME`, `APP_ADMIN_EMAIL` e `APP_ADMIN_PASSWORD` no arquivo `.env` antes de executar `docker compose -f compose.prod.yaml up -d --build`.

## 4. Validar o deploy

Após o serviço ficar disponível, valide:

```bash
curl https://SEU-SERVICO.onrender.com/actuator/health
```

A resposta deve indicar `UP`. Depois, entre pela interface usando:

- `solicitante@demo.local`;
- `tecnico@demo.local`;
- a senha definida em `APP_DEMO_PASSWORD`.

## Limitação dos anexos no plano gratuito

O sistema grava anexos no diretório local. Como o armazenamento do serviço gratuito do Render é temporário, arquivos enviados podem desaparecer após reinícios ou novos deploys. Os chamados, usuários, comentários e históricos permanecem no Neon. Para uso real, migre os anexos para armazenamento compatível com S3 ou use um disco persistente pago.
