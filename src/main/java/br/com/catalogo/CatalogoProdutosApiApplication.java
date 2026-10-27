package br.com.catalogo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * O ponto de entrada da aplicação.
 *
 * <p>{@code @SpringBootApplication} é um atalho para três anotações:
 *
 * <ul>
 *   <li>{@code @SpringBootConfiguration}: marca esta classe como fonte de
 *       definições de beans;</li>
 *   <li>{@code @EnableAutoConfiguration}: faz o Spring Boot configurar sozinho o
 *       que encontrar no <i>classpath</i> — como existe um driver do PostgreSQL e
 *       o Spring Data JPA, ele monta a conexão, o Hibernate e os repositórios sem
 *       que precisemos escrever isso;</li>
 *   <li>{@code @ComponentScan}: varre <b>este pacote e os subpacotes</b>
 *       procurando componentes ({@code @Service}, {@code @RestController},
 *       {@code @Configuration}) para instanciar e conectar.</li>
 * </ul>
 *
 * <p>É por causa do {@code @ComponentScan} que esta classe fica em
 * {@code br.com.catalogo}, acima de todos os outros pacotes: tudo fica dentro do
 * alcance da varredura.
 *
 * <p><b>O mapa do projeto</b>, na ordem em que uma requisição o percorre:
 *
 * <pre>
 *   controller/     a borda HTTP: rotas, status e cabeçalhos
 *        ↓
 *   service/        ProdutoService (interface) e ProdutoServiceImpl: orquestra e abre a transação
 *   service/regra/  as regras de negócio, uma por classe (Strategy)
 *        ↓
 *   repository/     o acesso ao banco, gerado pelo Spring Data
 *        ↓
 *   model/          a entidade JPA, espelho da tabela, com o próprio comportamento
 *
 *   dto/            o que entra e sai em JSON
 *   mapper/         a conversão entre DTO e entidade
 *   exception/      os erros de negócio e o tratador que os converte em HTTP
 *   config/         prefixo /api/v1 e documentação OpenAPI
 * </pre>
 *
 * <p>São as camadas clássicas de uma aplicação Spring (MVC). Os princípios SOLID
 * aparecem dentro delas; o README tem a tabela de onde cada um está.
 */
@SpringBootApplication
public class CatalogoProdutosApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogoProdutosApiApplication.class, args);
    }
}
