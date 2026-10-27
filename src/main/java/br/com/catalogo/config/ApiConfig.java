package br.com.catalogo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Aplica o prefixo de versão a todos os controllers de uma vez.
 *
 * <p><b>Por que não escrever {@code /api/v1} em cada {@code @RequestMapping}?</b>
 * Porque a decisão de versionamento ficaria repetida em cada controller, sujeita
 * a erro de digitação, e o dia da {@code v2} viraria uma caçada por arquivos.
 * Aqui ela é uma propriedade ({@code api.base-path}, no {@code application.yml})
 * aplicada em um lugar só.
 *
 * <p>O prefixo alcança apenas quem está no pacote {@code controller} — o
 * Swagger UI e o {@code /v3/api-docs} vivem fora dele e continuam na raiz, que é
 * onde as ferramentas esperam encontrá-los.
 */
@Configuration
public class ApiConfig implements WebMvcConfigurer {

    private final String basePath;

    /**
     * {@code @Value} injeta o valor de uma propriedade de configuração. O trecho
     * depois dos dois-pontos é o padrão usado caso a propriedade não exista.
     */
    public ApiConfig(@Value("${api.base-path:/api/v1}") String basePath) {
        this.basePath = basePath;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurador) {
        configurador.addPathPrefix(basePath,
                HandlerTypePredicate.forBasePackage("br.com.catalogo.controller"));
    }
}
