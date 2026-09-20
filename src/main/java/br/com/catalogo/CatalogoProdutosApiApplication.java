package br.com.catalogo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da aplicação.
 *
 * <p>{@code @SpringBootApplication} liga três coisas: configuração automática,
 * varredura de componentes a partir deste pacote e registro desta classe como
 * configuração. Por isso ela vive na raiz do pacote {@code br.com.catalogo} —
 * tudo abaixo dela é encontrado sem configuração adicional.
 */
@SpringBootApplication
public class CatalogoProdutosApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogoProdutosApiApplication.class, args);
    }
}
