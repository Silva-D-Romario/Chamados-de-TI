# Deploy gratuito com Render e Neon

Esta configuração usa o Render para executar a imagem Docker e o Neon para manter o PostgreSQL. O banco externo evita a expiração de 30 dias do PostgreSQL gratuito do Render.

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

## 3. Validar o deploy

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
