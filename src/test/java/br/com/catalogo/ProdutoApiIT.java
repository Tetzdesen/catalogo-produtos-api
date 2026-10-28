package br.com.catalogo;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.catalogo.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A aplicação inteira contra um PostgreSQL de verdade.
 *
 * <p>Os testes unitários usam mocks no lugar do banco, então não conseguem
 * conferir o que só o PostgreSQL faz: a migração do Flyway, o
 * {@code ddl-auto: validate} batendo entidade com tabela, a busca com
 * {@code unaccent}, o escape do {@code LIKE}, a constraint de SKU único e a
 * gravação no commit da transação. É isso que este teste cobre.
 *
 * <p>{@code @Testcontainers} e {@code @Container} sobem o contêiner uma vez para
 * a classe toda (o campo é {@code static}). {@code @ServiceConnection} faz o
 * Spring Boot apontar o datasource para ele, sem precisar de {@code DB_URL}.
 *
 * <p>A segurança fica de fora de propósito, já coberta pelo {@code SegurancaTest}:
 * o {@code JwtDecoder} é mock, e as escritas usam o {@code jwt()} do Spring
 * Security Test, que entrega o usuário já autenticado. As variáveis
 * {@code JWT_*} recebem valores fictícios só porque o {@code application.yml}
 * as exige.
 *
 * <p>O nome termina em {@code IT}: quem roda esta classe é o Failsafe, no
 * {@code mvn verify}, e não o Surefire do {@code mvn test}. Requer Docker.
 */
@Testcontainers
@SpringBootTest(properties = {
        "JWT_ISSUER_URI=https://emissor.invalid/realms/catalogo",
        "JWT_AUDIENCE=catalogo-api"})
@AutoConfigureMockMvc
class ProdutoApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProdutoRepository repositorio;

    @MockitoBean
    private JwtDecoder decodificador;

    // O banco é o mesmo para todos os testes da classe: cada um começa vazio.
    @BeforeEach
    void limparCatalogo() {
        repositorio.deleteAll();
    }

    @Test
    void cadastraEConsultaPeloLocationDevolvido() throws Exception {
        String local = cadastrar("CAF-001", "Café Torrado", "29.90")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(local))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("CAF-001"))
                .andExpect(jsonPath("$.preco").value(29.90))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void buscaPorNomeIgnoraAcentosEMaiusculas() throws Exception {
        cadastrar("CAF-001", "Café Torrado", "29.90");
        cadastrar("CAF-002", "CAFETEIRA Italiana", "120.00");
        cadastrar("CHA-001", "Chá Verde", "15.00");

        mvc.perform(get("/api/v1/produtos").param("nome", "cafe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.conteudo[*].sku", containsInAnyOrder("CAF-001", "CAF-002")));
    }

    @Test
    void buscaTrataCuringasDoLikeComoTexto() throws Exception {
        cadastrar("DES-050", "Desconto 50%", "10.00");
        cadastrar("DES-500", "Desconto 500", "10.00");

        // Sem o escape, '%' seria curinga e "50%" encontraria também "500".
        mvc.perform(get("/api/v1/produtos").param("nome", "50%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[*].sku", contains("DES-050")));
    }

    @Test
    void ordenaPeloCampoPedido() throws Exception {
        cadastrar("P-1", "Barato", "5.00");
        cadastrar("P-2", "Caro", "50.00");
        cadastrar("P-3", "Médio", "20.00");

        mvc.perform(get("/api/v1/produtos").param("sort", "preco,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[*].sku", contains("P-2", "P-3", "P-1")));
    }

    @Test
    void tamanhoDePaginaAcimaDoTetoViraCem() throws Exception {
        mvc.perform(get("/api/v1/produtos").param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamanho").value(100));
    }

    @Test
    void skuRepetidoNoCadastroDevolve409() throws Exception {
        cadastrar("CAF-001", "Café Torrado", "29.90");

        cadastrar("CAF-001", "Outro Café", "19.90")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("'CAF-001'")));
    }

    @Test
    void atualizacaoGravaNoCommitSemChamarSave() throws Exception {
        String local = localDe(cadastrar("CAF-001", "Café Torrado", "29.90"));

        mvc.perform(put(local).with(jwt()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"sku":"CAF-001","nome":"Café Especial","preco":34.90,"ativo":false}"""))
                .andExpect(status().isOk());

        // Uma nova requisição lê do banco: a mudança foi mesmo gravada.
        mvc.perform(get(local))
                .andExpect(jsonPath("$.nome").value("Café Especial"))
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void atualizarParaSkuDeOutroProdutoDevolve409ComAMensagemDaRegra() throws Exception {
        cadastrar("CAF-001", "Café Torrado", "29.90");
        String local = localDe(cadastrar("CAF-002", "Café Moído", "24.90"));

        // A regra roda antes de a entidade mudar. Se rodasse depois, o auto-flush
        // do Hibernate esbarraria na constraint e a mensagem seria a genérica.
        mvc.perform(put(local).with(jwt()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"sku":"CAF-001","nome":"Café Moído","preco":24.90,"ativo":true}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("'CAF-001'")));
    }

    @Test
    void exclusaoRemoveDoBanco() throws Exception {
        String local = localDe(cadastrar("CAF-001", "Café Torrado", "29.90"));

        mvc.perform(delete(local).with(jwt())).andExpect(status().isNoContent());

        mvc.perform(get(local))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    void documentacaoFicaDesligadaForaDoPerfilDev() throws Exception {
        // Este teste roda sem perfil, como o jar em produção.
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }

    private ResultActions cadastrar(String sku, String nome, String preco)
            throws Exception {
        return mvc.perform(post("/api/v1/produtos").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sku":"%s","nome":"%s","preco":%s}""".formatted(sku, nome, preco)));
    }

    private static String localDe(ResultActions cadastro) {
        return cadastro.andReturn().getResponse().getHeader("Location");
    }
}
