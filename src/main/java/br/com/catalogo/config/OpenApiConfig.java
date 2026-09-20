package br.com.catalogo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados da documentação OpenAPI.
 *
 * <p>Só os dados de capa ficam aqui. A descrição de cada endpoint, dos parâmetros
 * e dos schemas é derivada pelo springdoc dos próprios controllers e DTOs — spec
 * escrita à mão diverge do código na primeira semana (ver README, decisão 12).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI catalogoOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Catálogo de Produtos API")
                .version("v1")
                .description("""
                        API REST para cadastro de produtos e categorias e para o controle de \
                        estoque por movimentações (entrada, saída e ajuste).

                        Erros seguem o formato RFC 9457 (application/problem+json).""")
                .contact(new Contact().name("Equipe Catálogo"))
                .license(new License().name("MIT")));
    }
}
