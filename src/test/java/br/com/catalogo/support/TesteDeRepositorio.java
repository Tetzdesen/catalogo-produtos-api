package br.com.catalogo.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

/**
 * Atalho para os testes de persistência.
 *
 * <p>{@code @DataJpaTest} sobe só a fatia de JPA — rápido, e cada teste roda em
 * transação revertida ao final. {@code replace = NONE} é essencial: sem ele o
 * Spring trocaria nosso H2 configurado (em modo PostgreSQL, com as migrações do
 * Flyway) por um banco embarcado genérico criado pelo Hibernate, e o teste
 * deixaria de exercitar o schema real.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public @interface TesteDeRepositorio {
}
