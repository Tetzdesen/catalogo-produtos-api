package br.com.catalogo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Os dados de capa da documentação OpenAPI.
 *
 * <p>Só o título, a versão e a descrição estão aqui. A descrição de cada
 * endpoint, de cada parâmetro e de cada campo é <b>derivada pelo springdoc</b> a
 * partir dos controllers e dos DTOs, com ajuda das anotações
 * {@code @Operation} e {@code @Parameter} usadas neles.
 *
 * <p>Uma especificação escrita à mão em um arquivo separado começa correta e
 * diverge do código na primeira semana — normalmente sem ninguém notar, porque
 * nada quebra quando a documentação mente. Gerada a partir do código, ela erra
 * apenas na medida em que o código erra.
 *
 * <p>{@code @Bean} diz ao Spring: "chame este método, guarde o objeto que ele
 * devolver e entregue-o a quem precisar". Quem precisa, aqui, é o springdoc.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI catalogoOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Catálogo de Produtos API")
                .version("v1")
                .description("""
                        API REST básica de catálogo de produtos: cadastro, consulta, \
                        listagem paginada com filtro por nome, atualização e exclusão.

                        Erros seguem o formato RFC 9457 (application/problem+json).""")
                .license(new License().name("MIT")));
    }
}
