# Virta — Back-end

API REST do **Virta**, sistema de gestão financeira pessoal e compartilhada, feita com **Spring Boot 4 (Java 17)**, Spring Security (JWT), JPA/Hibernate e MySQL.

**A documentação completa do projeto** (funcionalidades, arquitetura, decisões, segurança e variáveis de ambiente) está no README do repositório principal: **https://github.com/joaolucaspinheiro/virta**

| | |
|---|---|
| API em produção | https://virta-back-end.onrender.com |
| Swagger | https://virta-back-end.onrender.com/swagger-ui/index.html |
| Aplicação | https://virta-front-end.vercel.app |

> Plano gratuito do Render: a primeira requisição depois de um tempo parado pode levar cerca de 1 minuto.

## Rodar localmente

Pré-requisitos: Java 17+ e MySQL 8 (ou MariaDB) na porta 3306.

```bash
cp src/main/resources/application-secrets-example.properties src/main/resources/application-secrets.properties
# preencha usuário/senha do banco e a chave JWT (openssl rand -base64 32)
./mvnw spring-boot:run
```

- API: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui/index.html` (faça login em `POST /auth/login` e use o botão **Authorize** com o token)

## Testes

```bash
./mvnw test
```

## Estrutura

```
src/main/java/br/com/virta/backend
├── config       segurança (JWT, CORS, cabeçalhos), rate limit, Swagger
├── controller   rotas REST
├── dto          records de entrada/saída com validação
├── event        eventos de domínio (tempo real)
├── exception    exceções de negócio + GlobalExceptionHandler
├── model        entidades JPA
├── repository   Spring Data JPA
└── service      regras de negócio e controle de acesso por papel
src/main/resources/templates/email   templates HTML dos e-mails
```

## Deploy

Imagem Docker (`Dockerfile` na raiz) publicada no Render; banco MySQL no Aiven (conexão com SSL). As variáveis de ambiente necessárias estão listadas no README principal.
