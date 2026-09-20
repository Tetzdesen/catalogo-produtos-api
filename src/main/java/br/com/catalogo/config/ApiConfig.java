package br.com.catalogo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.method.HandlerTypePredicate;

/**
 * Aplica o prefixo de versão a todos os controllers de uma vez.
 *
 * <p>A alternativa seria repetir {@code /api/v1} em cada {@code @RequestMapping},
 * o que convida ao erro de digitação e espalha a decisão de versionamento por
 * todo o código. Aqui o prefixo é uma propriedade ({@code api.base-path}) aplicada
 * a quem estiver no pacote {@code controller}; o Swagger continua fora dele,
 * acessível na raiz.
 */
@Configuration
public class ApiConfig implements WebMvcConfigurer {

    private final String basePath;

    public ApiConfig(@Value("${api.base-path:/api/v1}") String basePath) {
        this.basePath = basePath;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurador) {
        configurador.addPathPrefix(basePath,
                HandlerTypePredicate.forBasePackage("br.com.catalogo.controller"));
    }
}
