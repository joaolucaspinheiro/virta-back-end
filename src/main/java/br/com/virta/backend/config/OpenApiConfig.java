package br.com.virta.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI virtaOpenAPI() {
        String schemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Virta API")
                        .version("1.0")
                        .description("""
                                API do **Virta**, sistema de finanças pessoais e compartilhadas.

                                **Como testar as rotas protegidas**
                                1. Faça login em `POST /auth/login` e copie o `token` da resposta.
                                2. Clique em **Authorize** e cole o token (sem a palavra "Bearer").

                                Erros vêm como `{"error": "mensagem"}`. Rotas de login e cadastro têm
                                limite de 5 tentativas por minuto (HTTP 429).
                                """)
                        .contact(new Contact().name("Virta").url("https://virta-front-end.vercel.app")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components()
                        .addSecuritySchemes(schemeName, new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}