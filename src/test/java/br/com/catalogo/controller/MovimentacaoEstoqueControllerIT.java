package br.com.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.catalogo.domain.Categoria;
import br.com.catalogo.domain.Produto;
import br.com.catalogo.repository.CategoriaRepository;
import br.com.catalogo.repository.MovimentacaoEstoqueRepository;
import br.com.catalogo.repository.ProdutoRepository;
import br.com.catalogo.support.Dados;
import br.com.catalogo.support.TesteDeIntegracao;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TesteDeIntegracao
@DisplayName("API de movimentações de estoque")
class MovimentacaoEstoqueControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private MovimentacaoEstoqueRepository movimentacaoRepository;

    private Produto produto;

    @BeforeEach
    void preparar() {
        movimentacaoRepository.deleteAll();
        produtoRepository.deleteAll();
        categoriaRepository.deleteAll();
        Categoria categoria = categoriaRepository.save(Dados.categoria("Ferramentas"));
        produto = produtoRepository.save(Dados.produto("MART-001", categoria));
    }

    private void entrada(int quantidade, String motivo) throws Exception {
        mockMvc.perform(post("/api/v1/produtos/{id}/movimentacoes/entradas", produto.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantidade": %d, "motivo": "%s"}""".formatted(quantidade, motivo)))
                .andExpect(status().isCreated());
    }

    @Nested
    @DisplayName("POST .../entradas")
    class Entrada {

        @Test
        @DisplayName("devolve 201 com a movimentação e o saldo resultante")
        void registraEntrada() throws Exception {
            mockMvc.perform(post("/api/v1/produtos/{id}/movimentacoes/entradas", produto.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantidade": 10, "motivo": "Compra do fornecedor"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tipo").value("ENTRADA"))
                    .andExpect(jsonPath("$.quantidade").value(10))
                    .andExpect(jsonPath("$.diferenca").value(10))
                    .andExpect(jsonPath("$.saldoResultante").value(10))
                    .andExpect(jsonPath("$.motivo").value("Compra do fornecedor"))
                    .andExpect(jsonPath("$.dataHora").exists());

            assertThat(produtoRepository.findById(produto.getId()).orElseThrow().getSaldoEstoque())
                    .isEqualTo(10);
        }

        @Test
        @DisplayName("devolve 400 para quantidade zero, sem alterar o saldo")
        void rejeitaQuantidadeZero() throws Exception {
            mockMvc.perform(post("/api/v1/produtos/{id}/movimentacoes/entradas", produto.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantidade": 0, "motivo": "Compra"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.campos[0].campo").value("quantidade"));

            assertThat(produtoRepository.findById(produto.getId()).orElseThrow().getSaldoEstoque()).isZero();
        }

        @Test
        @DisplayName("devolve 404 para produto inexistente")
        void produtoInexistente() throws Exception {
            mockMvc.perform(post("/api/v1/produtos/{id}/movimentacoes/entradas", 999_999)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantidade": 10, "motivo": "Compra"}"""))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST .../saidas")
    class Saida {

        @Test
        @DisplayName("devolve 201 e reduz o saldo")
        void registraSaida() throws Exception {
            entrada(10, "Compra inicial");

            mockMvc.perform(post("/api/v1/produtos/{id}/movimentacoes/saidas", produto.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantidade": 3, "motivo": "Venda"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tipo").value("SAIDA"))
                    .andExpect(jsonPath("$.diferenca").value(-3))
                    .andExpect(jsonPath("$.saldoResultante").value(7));
        }

        @Test
        @DisplayName("devolve 409 quando a saída é maior que o saldo e preserva o saldo")
        void saldoInsuficiente() throws Exception {
            entrada(10, "Compra inicial");

            mockMvc.perform(post("/api/v1/produtos/{id}/movimentacoes/saidas", produto.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantidade": 12, "motivo": "Venda"}"""))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value(Matchers.containsString("disponível 10")));

            assertThat(produtoRepository.findById(produto.getId()).orElseThrow().getSaldoEstoque())
                    .isEqualTo(10);
            assertThat(movimentacaoRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("devolve 409 para produto inativo")
        void produtoInativo() throws Exception {
            entrada(10, "Compra inicial");
            Produto inativo = produtoRepository.findById(produto.getId()).orElseThrow();
            inativo.inativar();
            produtoRepository.save(inativo);

            mockMvc.perform(post("/api/v1/produtos/{id}/movimentacoes/saidas", produto.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantidade": 1, "motivo": "Venda"}"""))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(Matchers.containsString("inativo")));
        }
    }

    @Nested
    @DisplayName("POST .../ajustes")
    class Ajuste {

        @Test
        @DisplayName("ajusta para cima registrando a diferença positiva")
        void ajustaParaCima() throws Exception {
            entrada(12, "Compra inicial");

            mockMvc.perform(post("/api/v1/produtos/{id}/movimentacoes/ajustes", produto.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantidade": 20, "motivo": "Inventário"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tipo").value("AJUSTE"))
                    .andExpect(jsonPath("$.diferenca").value(8))
                    .andExpect(jsonPath("$.saldoResultante").value(20));
        }

        @Test
        @DisplayName("ajusta para baixo registrando a diferença negativa")
        void ajustaParaBaixo() throws Exception {
            entrada(12, "Compra inicial");

            mockMvc.perform(post("/api/v1/produtos/{id}/movimentacoes/ajustes", produto.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantidade": 5, "motivo": "Inventário"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.diferenca").value(-7))
                    .andExpect(jsonPath("$.saldoResultante").value(5));
        }

        @Test
        @DisplayName("devolve 400 para quantidade negativa")
        void rejeitaNegativo() throws Exception {
            mockMvc.perform(post("/api/v1/produtos/{id}/movimentacoes/ajustes", produto.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantidade": -1, "motivo": "Inventário"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos[0].campo").value("quantidade"));
        }

        @Test
        @DisplayName("devolve 400 quando o motivo está ausente")
        void rejeitaSemMotivo() throws Exception {
            mockMvc.perform(post("/api/v1/produtos/{id}/movimentacoes/ajustes", produto.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantidade": 5}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos[0].campo").value("motivo"))
                    .andExpect(jsonPath("$.campos[0].mensagem").value("O motivo é obrigatório."));
        }
    }

    @Nested
    @DisplayName("GET .../movimentacoes")
    class Historico {

        @Test
        @DisplayName("devolve as movimentações da mais recente para a mais antiga")
        void ordenadoPorDataDecrescente() throws Exception {
            entrada(10, "Compra inicial");
            mockMvc.perform(post("/api/v1/produtos/{id}/movimentacoes/saidas", produto.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantidade": 4, "motivo": "Venda"}"""))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/api/v1/produtos/{id}/movimentacoes", produto.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElementos").value(2))
                    .andExpect(jsonPath("$.conteudo[0].tipo").value("SAIDA"))
                    .andExpect(jsonPath("$.conteudo[1].tipo").value("ENTRADA"));
        }

        @Test
        @DisplayName("filtra por tipo")
        void filtraPorTipo() throws Exception {
            entrada(10, "Compra inicial");
            mockMvc.perform(post("/api/v1/produtos/{id}/movimentacoes/saidas", produto.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantidade": 4, "motivo": "Venda"}"""))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/api/v1/produtos/{id}/movimentacoes", produto.getId())
                            .param("tipo", "SAIDA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElementos").value(1))
                    .andExpect(jsonPath("$.conteudo[0].motivo").value("Venda"));
        }

        @Test
        @DisplayName("devolve lista vazia para produto nunca movimentado")
        void semMovimentacoes() throws Exception {
            mockMvc.perform(get("/api/v1/produtos/{id}/movimentacoes", produto.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conteudo").isEmpty())
                    .andExpect(jsonPath("$.totalElementos").value(0));
        }

        @Test
        @DisplayName("devolve 404 para produto inexistente, e não uma página vazia")
        void produtoInexistente() throws Exception {
            mockMvc.perform(get("/api/v1/produtos/{id}/movimentacoes", 999_999))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Nested
    @DisplayName("imutabilidade do histórico")
    class Imutabilidade {

        @Test
        @DisplayName("não existe rota para alterar ou excluir uma movimentação")
        void semRotasDeAlteracao() throws Exception {
            entrada(10, "Compra inicial");
            Long movimentacaoId = movimentacaoRepository.findAll().getFirst().getId();

            mockMvc.perform(put("/api/v1/produtos/{p}/movimentacoes/{m}", produto.getId(), movimentacaoId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantidade": 999, "motivo": "Fraude"}"""))
                    .andExpect(status().is4xxClientError());

            mockMvc.perform(delete("/api/v1/produtos/{p}/movimentacoes/{m}", produto.getId(), movimentacaoId))
                    .andExpect(status().is4xxClientError());

            // O lançamento original continua intacto.
            assertThat(movimentacaoRepository.count()).isEqualTo(1);
            assertThat(movimentacaoRepository.findAll().getFirst().getQuantidade()).isEqualTo(10);
        }
    }
}
