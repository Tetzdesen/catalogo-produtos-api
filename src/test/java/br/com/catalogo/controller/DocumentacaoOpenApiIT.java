package br.com.catalogo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.catalogo.support.TesteDeIntegracao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Garante que a documentação continua sendo gerada.
 *
 * <p>Uma spec OpenAPI quebrada só aparece quando alguém abre o Swagger — o que
 * costuma acontecer tarde. Este teste transforma isso em falha de build, e de
 * quebra confirma que o prefixo {@code /api/v1} aplicado por {@code ApiConfig}
 * aparece na documentação.
 */
@TesteDeIntegracao
@DisplayName("Documentação OpenAPI")
class DocumentacaoOpenApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("expõe a spec em /v3/api-docs com os metadados e os caminhos versionados")
    void exposicaoDaSpec() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("Catálogo de Produtos API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.paths['/api/v1/categorias']").exists());
    }

    @Test
    @DisplayName("serve a interface do Swagger UI")
    void swaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
