package br.com.catalogo.config;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.catalogo.controller.ProdutoController;
import br.com.catalogo.dto.ProdutoResponse;
import br.com.catalogo.service.ProdutoService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * As regras da {@link SegurancaConfig}, conferidas por HTTP.
 *
 * <p>{@code @WebMvcTest} sobe só a camada web (controller, tratador de erros e
 * segurança), sem banco. O service é um mock, e o {@code JwtDecoder} também: o
 * {@code jwt()} do Spring Security Test entrega o usuário já autenticado, sem
 * precisar de um provedor de verdade para assinar o token.
 *
 * <p>As variáveis do provedor recebem valores fictícios só porque o
 * {@code application.yml} as exige; com o decodificador mockado, nada é
 * buscado nesses endereços.
 */
@WebMvcTest(controllers = ProdutoController.class, properties = {
        "JWT_ISSUER_URI=https://emissor.invalid/realms/catalogo",
        "JWT_AUDIENCE=catalogo-api"})
@Import({SegurancaConfig.class, ApiConfig.class})
class SegurancaTest {

    private static final String CORPO = """
            {"sku":"CAF-001","nome":"Café Torrado","preco":24.90}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProdutoService service;

    @MockitoBean
    private JwtDecoder decodificador;

    @Test
    void consultaNaoExigeToken() throws Exception {
        when(service.buscar(1L)).thenReturn(cafe());

        mvc.perform(get("/api/v1/produtos/1")).andExpect(status().isOk());
    }

    @Test
    void cadastroSemTokenDevolve401NoFormatoDaApi() throws Exception {
        mvc.perform(post("/api/v1/produtos").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.type").value("urn:catalogo:erro:nao-autenticado"));

        verify(service, never()).criar(any());
    }

    @Test
    void cadastroComTokenChegaAoService() throws Exception {
        when(service.criar(any())).thenReturn(cafe());

        mvc.perform(post("/api/v1/produtos").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isCreated());
    }

    @Test
    void exclusaoSemTokenDevolve401() throws Exception {
        mvc.perform(delete("/api/v1/produtos/1")).andExpect(status().isUnauthorized());

        verify(service, never()).excluir(eq(1L));
    }

    private static ProdutoResponse cafe() {
        return new ProdutoResponse(1L, "CAF-001", "Café Torrado", null, new BigDecimal("24.90"), true);
    }
}
