package br.com.catalogo.service;

import br.com.catalogo.dto.PaginaResponse;
import br.com.catalogo.dto.ProdutoRequest;
import br.com.catalogo.dto.ProdutoResponse;
import br.com.catalogo.exception.OrdenacaoInvalidaException;
import br.com.catalogo.exception.ProdutoNaoEncontradoException;
import br.com.catalogo.mapper.ProdutoMapper;
import br.com.catalogo.model.Produto;
import br.com.catalogo.repository.ProdutoRepository;
import br.com.catalogo.service.regra.RegraDeProduto;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A implementação do catálogo: orquestra regras, mapper e repositório dentro de
 * uma transação.
 *
 * <p>Repare no que <b>não</b> está aqui: conversão de DTO (está no
 * {@code ProdutoMapper}), regras de negócio (cada uma em uma
 * {@code RegraDeProduto}), escape de SQL (no {@code ProdutoRepository}) e texto
 * de mensagem de erro (nas exceções). O service só coordena: é o "S" de SOLID.
 *
 * <p>{@code @Transactional} abre uma transação ao entrar no método e faz commit
 * ao sair (ou rollback, se uma exceção escapar). Em {@code atualizar}, não há
 * chamada a {@code save}: a entidade carregada está sendo acompanhada pelo
 * Hibernate, e as mudanças feitas nela são gravadas no commit.
 */
@Service
public class ProdutoServiceImpl implements ProdutoService {

    // Os campos aceitos em ?sort=. São os mesmos nomes do ProdutoResponse, e a
    // ordem é a usada na mensagem de erro.
    private static final List<String> CAMPOS_ORDENAVEIS =
            List.of("id", "sku", "nome", "descricao", "preco", "ativo");

    private final ProdutoRepository repositorio;
    private final ProdutoMapper mapper;
    private final List<RegraDeProduto> regras;

    // Injeção pelo construtor: as dependências são obrigatórias e ficam visíveis na
    // assinatura. Com um único construtor, o Spring dispensa @Autowired. Para a
    // lista, o Spring entrega todos os beans que implementam RegraDeProduto.
    public ProdutoServiceImpl(ProdutoRepository repositorio, ProdutoMapper mapper,
                              List<RegraDeProduto> regras) {
        this.repositorio = repositorio;
        this.mapper = mapper;
        this.regras = regras;
    }

    @Override
    @Transactional
    public ProdutoResponse criar(ProdutoRequest dados) {
        validar(dados, null);
        Produto produto = mapper.paraEntidade(dados);
        return mapper.paraResposta(repositorio.save(produto));
    }

    // readOnly avisa o Hibernate de que nada será alterado, e ele pula a
    // verificação de mudanças no fim da transação.
    @Override
    @Transactional(readOnly = true)
    public ProdutoResponse buscar(Long id) {
        return mapper.paraResposta(carregar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<ProdutoResponse> listar(String nome, Pageable paginacao) {
        validarOrdenacao(paginacao);
        Page<Produto> pagina = (nome == null || nome.isBlank())
                ? repositorio.findAll(paginacao)
                : repositorio.buscarPorNome(nome.strip(), paginacao);
        return PaginaResponse.de(pagina, mapper::paraResposta);
    }

    @Override
    @Transactional
    public ProdutoResponse atualizar(Long id, ProdutoRequest dados) {
        Produto produto = carregar(id);
        // As regras olham os dados de entrada ANTES de a entidade mudar; ver o
        // Javadoc de RegraDeProduto para o motivo.
        validar(dados, id);
        produto.atualizar(dados.sku(), dados.nome(), dados.descricao(), dados.preco(), dados.ativo());
        return mapper.paraResposta(produto);
    }

    @Override
    @Transactional
    public void excluir(Long id) {
        repositorio.delete(carregar(id));
    }

    /**
     * Sem esta conferência, um {@code ?sort=inexistente} chegaria ao Hibernate e
     * sairia como {@code 500}. A lista também impede ordenar por campos que não
     * fazem parte do contrato (um campo interno adicionado à entidade amanhã).
     */
    private void validarOrdenacao(Pageable paginacao) {
        paginacao.getSort().forEach(ordem -> {
            if (!CAMPOS_ORDENAVEIS.contains(ordem.getProperty())) {
                throw new OrdenacaoInvalidaException(ordem.getProperty(), CAMPOS_ORDENAVEIS);
            }
        });
    }

    private void validar(ProdutoRequest dados, Long idDoProduto) {
        regras.forEach(regra -> regra.validar(dados, idDoProduto));
    }

    private Produto carregar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new ProdutoNaoEncontradoException(id));
    }
}
