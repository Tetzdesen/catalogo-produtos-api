package br.com.catalogo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * O que responde quem abre a raiz ({@code /}) no navegador.
 *
 * <p>A API não tem página própria, e sem esta classe a raiz cairia no
 * {@code anyRequest().authenticated()} da {@link SegurancaConfig}: um {@code 401}
 * que, para quem só colou o endereço da demo, parece erro. Em vez disso, a raiz
 * redireciona para algo útil:
 * <ul>
 *   <li>o Swagger UI, quando a documentação está ligada (perfil dev ou
 *       {@code SWAGGER_HABILITADO=true}, como na demo);</li>
 *   <li>a listagem pública de produtos, quando não está. Redirecionar para um
 *       Swagger desligado levaria a um {@code 404}.</li>
 * </ul>
 *
 * <p>Um <b>view controller</b> é uma rota declarada sem classe de controller.
 * Por não estar no pacote {@code controller}, ela não recebe o prefixo
 * {@code /api/v1} da {@link ApiConfig}.
 */
@Configuration
public class PaginaInicialConfig implements WebMvcConfigurer {

    private final String destino;

    public PaginaInicialConfig(@Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerLigado,
                               @Value("${springdoc.swagger-ui.path:/swagger-ui.html}") String caminhoDoSwagger,
                               @Value("${api.base-path:/api/v1}") String basePath) {
        this.destino = swaggerLigado ? caminhoDoSwagger : basePath + "/produtos";
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registro) {
        registro.addRedirectViewController("/", destino);
    }
}
