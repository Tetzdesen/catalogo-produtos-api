package br.com.catalogo.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.catalogo.controller.ProdutoController;
import br.com.catalogo.service.ProdutoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A raiz com a documentação ligada, como na demo online: o destino passa a ser
 * o Swagger UI. O caso com a documentação desligada está no {@link SegurancaTest}.
 */
@WebMvcTest(controllers = ProdutoController.class, properties = {
        "JWT_ISSUER_URI=https://emissor.invalid/realms/catalogo",
        "JWT_AUDIENCE=catalogo-api",
        "SWAGGER_HABILITADO=true"})
@Import({SegurancaConfig.class, ApiConfig.class, PaginaInicialConfig.class})
class PaginaInicialComSwaggerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProdutoService service;

    @MockitoBean
    private JwtDecoder decodificador;

    @Test
    void raizRedirecionaParaOSwaggerQuandoEleEstaLigado() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/swagger-ui.html"));
    }
}
