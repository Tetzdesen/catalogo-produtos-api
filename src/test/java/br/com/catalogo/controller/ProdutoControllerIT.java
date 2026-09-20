package br.com.catalogo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.catalogo.domain.Categoria;
import br.com.catalogo.domain.MovimentacaoEstoque;
import br.com.catalogo.domain.Produto;
import br.com.catalogo.domain.TipoMovimentacao;
import br.com.catalogo.repository.CategoriaRepository;
import br.com.catalogo.repository.MovimentacaoEstoqueRepository;
import br.com.catalogo.repository.ProdutoRepository;
import br.com.catalogo.support.Dados;
import br.com.catalogo.support.TesteDeIntegracao;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TesteDeIntegracao
@DisplayName("API de produtos")
class ProdutoControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private MovimentacaoEstoqueRepository movimentacaoRepository;

    private Categoria ferramentas;

    @BeforeEach
    void preparar() {
        movimentacaoRepository.deleteAll();
        produtoRepository.deleteAll();
        categoriaRepository.deleteAll();
        ferramentas = categoriaRepository.save(Dados.categoria("Ferramentas"));
    }

    private String corpoValido(String sku) {
        return """
                {"sku": "%s", "nome": "Martelo de unha", "descricao": "Cabo de madeira",
                 "preco": 49.90, "categoriaId": %d}""".formatted(sku, ferramentas.getId());
    }

    @Nested
    @DisplayName("POST /api/v1/produtos")
    class Criar {

        @Test
        @DisplayName("devolve 201 com o produto ativo, saldo zero e a categoria aninhada")
        void criaComSucesso() throws Exception {
            mockMvc.perform(post("/api/v1/produtos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoValido("MART-001")))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", Matchers.matchesPattern("/api/v1/produtos/\\d+")))
                    .andExpect(jsonPath("$.sku").value("MART-001"))
                    .andExpect(jsonPath("$.preco").value(49.90))
                    .andExpect(jsonPath("$.ativo").value(true))
                    .andExpect(jsonPath("$.saldoEstoque").value(0))
                    .andExpect(jsonPath("$.categoria.nome").value("Ferramentas"));
        }

        @Test
        @DisplayName("devolve 400 listando todos os campos obrigatórios ausentes")
        void rejeitaCamposAusentes() throws Exception {
            mockMvc.perform(post("/api/v1/produtos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.campos[*].campo",
                            Matchers.containsInAnyOrder("sku", "nome", "preco", "categoriaId")));
        }

        @Test
        @DisplayName("devolve 400 quando o preço não é positivo")
        void rejeitaPrecoNaoPositivo() throws Exception {
            mockMvc.perform(post("/api/v1/produtos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"sku": "MART-001", "nome": "Martelo", "preco": 0, "categoriaId": %d}"""
                                    .formatted(ferramentas.getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos[0].campo").value("preco"));
        }

        @Test
        @DisplayName("devolve 409 quando o SKU já existe")
        void rejeitaSkuDuplicado() throws Exception {
            produtoRepository.save(Dados.produto("MART-001", ferramentas));

            mockMvc.perform(post("/api/v1/produtos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoValido("MART-001")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(Matchers.containsString("SKU")));
        }

        @Test
        @DisplayName("devolve 404 quando a categoria não existe")
        void rejeitaCategoriaInexistente() throws Exception {
            mockMvc.perform(post("/api/v1/produtos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"sku": "MART-001", "nome": "Martelo", "preco": 10.00, "categoriaId": 999999}"""))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value(Matchers.containsString("Categoria")));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/produtos")
    class Listar {

        @BeforeEach
        void popular() {
            produtoRepository.save(Dados.produto("MART-001", "Martelo de unha", "49.90", ferramentas));
            produtoRepository.save(Dados.produto("SERR-002", "Serrote 20 polegadas", "89.90", ferramentas));
            Produto inativo = Dados.produto("ALIC-003", "Alicate universal", "35.00", ferramentas);
            inativo.inativar();
            produtoRepository.save(inativo);
        }

        @Test
        @DisplayName("sem filtros devolve a página com os metadados de paginação")
        void listaSemFiltros() throws Exception {
            mockMvc.perform(get("/api/v1/produtos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conteudo.length()").value(3))
                    .andExpect(jsonPath("$.pagina").value(0))
                    .andExpect(jsonPath("$.totalElementos").value(3))
                    .andExpect(jsonPath("$.totalPaginas").value(1));
        }

        @Test
        @DisplayName("combina filtros de nome, categoria e faixa de preço")
        void combinaFiltros() throws Exception {
            mockMvc.perform(get("/api/v1/produtos")
                            .param("nome", "martelo")
                            .param("categoriaId", ferramentas.getId().toString())
                            .param("precoMinimo", "40.00")
                            .param("precoMaximo", "60.00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElementos").value(1))
                    .andExpect(jsonPath("$.conteudo[0].sku").value("MART-001"));
        }

        @Test
        @DisplayName("filtro de situação ativa não devolve produtos inativos")
        void filtraPorSituacao() throws Exception {
            mockMvc.perform(get("/api/v1/produtos").param("ativo", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conteudo[*].sku",
                            Matchers.not(Matchers.hasItem("ALIC-003"))))
                    .andExpect(jsonPath("$.totalElementos").value(2));
        }

        @Test
        @DisplayName("devolve lista vazia quando nada corresponde")
        void semCorrespondencia() throws Exception {
            mockMvc.perform(get("/api/v1/produtos").param("nome", "inexistente"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conteudo").isEmpty())
                    .andExpect(jsonPath("$.totalElementos").value(0));
        }

        @Test
        @DisplayName("devolve 400 quando o preço mínimo é maior que o máximo")
        void rejeitaFaixaInvertida() throws Exception {
            mockMvc.perform(get("/api/v1/produtos")
                            .param("precoMinimo", "100.00")
                            .param("precoMaximo", "10.00"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value(Matchers.containsString("faixa de preço")));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/produtos/{id}")
    class Buscar {

        @Test
        @DisplayName("devolve 200 com o produto")
        void buscaExistente() throws Exception {
            Produto produto = produtoRepository.save(Dados.produto("MART-001", ferramentas));

            mockMvc.perform(get("/api/v1/produtos/{id}", produto.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sku").value("MART-001"))
                    .andExpect(jsonPath("$.categoria.id").value(ferramentas.getId()));
        }

        @Test
        @DisplayName("devolve 404 quando não existe")
        void naoEncontrado() throws Exception {
            mockMvc.perform(get("/api/v1/produtos/{id}", 999_999))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/produtos/{id}")
    class Atualizar {

        @Test
        @DisplayName("devolve 200 e preserva o saldo de estoque")
        void atualizaComSucesso() throws Exception {
            Produto produto = produtoRepository.save(Dados.produto("MART-001", ferramentas));
            registrarEntrada(produto, 15);

            mockMvc.perform(put("/api/v1/produtos/{id}", produto.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"sku": "MART-001", "nome": "Martelo profissional",
                                     "preco": 59.90, "categoriaId": %d}""".formatted(ferramentas.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nome").value("Martelo profissional"))
                    .andExpect(jsonPath("$.preco").value(59.90))
                    .andExpect(jsonPath("$.saldoEstoque").value(15));
        }

        @Test
        @DisplayName("devolve 409 ao tentar alterar o SKU")
        void rejeitaAlteracaoDeSku() throws Exception {
            Produto produto = produtoRepository.save(Dados.produto("MART-001", ferramentas));

            mockMvc.perform(put("/api/v1/produtos/{id}", produto.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoValido("OUTRO-SKU")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(Matchers.containsString("imutável")));
        }
    }

    @Nested
    @DisplayName("PATCH de ativação e inativação")
    class AlterarSituacao {

        @Test
        @DisplayName("inativa e reativa o produto")
        void alternaSituacao() throws Exception {
            Produto produto = produtoRepository.save(Dados.produto("MART-001", ferramentas));

            mockMvc.perform(patch("/api/v1/produtos/{id}/inativacao", produto.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ativo").value(false));

            mockMvc.perform(patch("/api/v1/produtos/{id}/ativacao", produto.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ativo").value(true));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/produtos/{id}")
    class Excluir {

        @Test
        @DisplayName("devolve 204 para produto sem movimentações")
        void excluiSemHistorico() throws Exception {
            Produto produto = produtoRepository.save(Dados.produto("MART-001", ferramentas));

            mockMvc.perform(delete("/api/v1/produtos/{id}", produto.getId()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("devolve 409 para produto com histórico e orienta a inativação")
        void bloqueiaComHistorico() throws Exception {
            Produto produto = produtoRepository.save(Dados.produto("MART-001", ferramentas));
            registrarEntrada(produto, 5);

            mockMvc.perform(delete("/api/v1/produtos/{id}", produto.getId()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(Matchers.containsString("inativação")));
        }
    }

    /** Registra uma entrada direto no repositório, sem passar pela API. */
    private void registrarEntrada(Produto produto, int quantidade) {
        produto.creditar(quantidade);
        produtoRepository.save(produto);
        movimentacaoRepository.save(new MovimentacaoEstoque(produto, TipoMovimentacao.ENTRADA,
                quantidade, quantidade, produto.getSaldoEstoque(), "Carga de teste", Instant.now()));
    }
}
