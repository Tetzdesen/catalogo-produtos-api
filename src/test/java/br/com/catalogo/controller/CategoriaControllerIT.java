package br.com.catalogo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.catalogo.domain.Categoria;
import br.com.catalogo.repository.CategoriaRepository;
import br.com.catalogo.repository.MovimentacaoEstoqueRepository;
import br.com.catalogo.repository.ProdutoRepository;
import br.com.catalogo.support.Dados;
import br.com.catalogo.support.TesteDeIntegracao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TesteDeIntegracao
@DisplayName("API de categorias")
class CategoriaControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private MovimentacaoEstoqueRepository movimentacaoRepository;

    @BeforeEach
    void limpar() {
        movimentacaoRepository.deleteAll();
        produtoRepository.deleteAll();
        categoriaRepository.deleteAll();
    }

    @Nested
    @DisplayName("POST /api/v1/categorias")
    class Criar {

        @Test
        @DisplayName("devolve 201 com Location e o corpo da categoria criada")
        void criaComSucesso() throws Exception {
            mockMvc.perform(post("/api/v1/categorias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nome": "Ferramentas", "descricao": "Itens de oficina"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                            "/api/v1/categorias/\\d+")))
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.nome").value("Ferramentas"))
                    .andExpect(jsonPath("$.descricao").value("Itens de oficina"));
        }

        @Test
        @DisplayName("devolve 400 no formato RFC 9457 quando o nome é muito curto")
        void rejeitaNomeInvalido() throws Exception {
            mockMvc.perform(post("/api/v1/categorias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nome": "A"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Dados inválidos"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.campos[0].campo").value("nome"))
                    .andExpect(jsonPath("$.campos[0].mensagem").value(
                            "O nome deve ter entre 2 e 60 caracteres."));
        }

        @Test
        @DisplayName("devolve 400 quando o nome está ausente")
        void rejeitaNomeAusente() throws Exception {
            mockMvc.perform(post("/api/v1/categorias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos[0].campo").value("nome"));
        }

        @Test
        @DisplayName("devolve 409 quando o nome já existe, ignorando a caixa")
        void rejeitaNomeDuplicado() throws Exception {
            categoriaRepository.save(Dados.categoria("Ferramentas"));

            mockMvc.perform(post("/api/v1/categorias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nome": "FERRAMENTAS"}"""))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Conflito com o estado atual"))
                    .andExpect(jsonPath("$.detail").value(
                            org.hamcrest.Matchers.containsString("Já existe uma categoria")));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/categorias")
    class Listar {

        @Test
        @DisplayName("devolve a página com os metadados de paginação")
        void listaPaginado() throws Exception {
            categoriaRepository.save(Dados.categoria("Ferramentas"));
            categoriaRepository.save(Dados.categoria("Jardinagem"));
            categoriaRepository.save(Dados.categoria("Pintura"));

            mockMvc.perform(get("/api/v1/categorias").param("page", "0").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conteudo.length()").value(2))
                    .andExpect(jsonPath("$.pagina").value(0))
                    .andExpect(jsonPath("$.tamanho").value(2))
                    .andExpect(jsonPath("$.totalElementos").value(3))
                    .andExpect(jsonPath("$.totalPaginas").value(2))
                    // Ordenação padrão por nome.
                    .andExpect(jsonPath("$.conteudo[0].nome").value("Ferramentas"));
        }

        @Test
        @DisplayName("limita o tamanho da página ao teto configurado")
        void limitaTamanhoDaPagina() throws Exception {
            mockMvc.perform(get("/api/v1/categorias").param("size", "5000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tamanho").value(100));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/categorias/{id}")
    class Buscar {

        @Test
        @DisplayName("devolve 200 com a categoria existente")
        void buscaExistente() throws Exception {
            Categoria categoria = categoriaRepository.save(Dados.categoria("Ferramentas"));

            mockMvc.perform(get("/api/v1/categorias/{id}", categoria.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nome").value("Ferramentas"));
        }

        @Test
        @DisplayName("devolve 404 no formato RFC 9457 quando não existe")
        void naoEncontrado() throws Exception {
            mockMvc.perform(get("/api/v1/categorias/{id}", 999_999))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Recurso não encontrado"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.type").value("urn:catalogo:erro:nao-encontrado"));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/categorias/{id}")
    class Atualizar {

        @Test
        @DisplayName("devolve 200 com os dados atualizados")
        void atualizaComSucesso() throws Exception {
            Categoria categoria = categoriaRepository.save(Dados.categoria("Ferramentas"));

            mockMvc.perform(put("/api/v1/categorias/{id}", categoria.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nome": "Ferramentas Elétricas", "descricao": "Nova descrição"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nome").value("Ferramentas Elétricas"))
                    .andExpect(jsonPath("$.descricao").value("Nova descrição"));
        }

        @Test
        @DisplayName("devolve 409 quando o nome pertence a outra categoria")
        void rejeitaNomeDeOutra() throws Exception {
            categoriaRepository.save(Dados.categoria("Jardinagem"));
            Categoria categoria = categoriaRepository.save(Dados.categoria("Ferramentas"));

            mockMvc.perform(put("/api/v1/categorias/{id}", categoria.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nome": "Jardinagem"}"""))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/categorias/{id}")
    class Excluir {

        @Test
        @DisplayName("devolve 204 quando a categoria não tem produtos")
        void excluiComSucesso() throws Exception {
            Categoria categoria = categoriaRepository.save(Dados.categoria("Jardinagem"));

            mockMvc.perform(delete("/api/v1/categorias/{id}", categoria.getId()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("devolve 409 quando existem produtos vinculados")
        void bloqueiaComProdutos() throws Exception {
            Categoria categoria = categoriaRepository.save(Dados.categoria("Ferramentas"));
            produtoRepository.save(Dados.produto("MART-001", categoria));

            mockMvc.perform(delete("/api/v1/categorias/{id}", categoria.getId()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(
                            org.hamcrest.Matchers.containsString("produtos vinculados")));
        }
    }
}
