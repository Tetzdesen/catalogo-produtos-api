package br.com.catalogo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Teste de fumaça.
 *
 * <p>Subir o contexto já prova bastante coisa: as migrações do Flyway aplicam,
 * o Hibernate valida cada entidade contra o schema criado por elas
 * ({@code ddl-auto: validate}) e todos os beans se resolvem. Uma divergência
 * entre entidade e tabela derruba este teste — que é exatamente o ponto.
 */
@SpringBootTest
@ActiveProfiles("test")
class CatalogoProdutosApiApplicationTests {

    @Test
    @DisplayName("O contexto da aplicação sobe com as migrações aplicadas e as entidades validadas")
    void contextLoads() {
    }
}
