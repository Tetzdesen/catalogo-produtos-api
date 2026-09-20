package br.com.catalogo.service;

import br.com.catalogo.domain.Categoria;
import br.com.catalogo.dto.CategoriaRequest;
import br.com.catalogo.exception.RecursoNaoEncontradoException;
import br.com.catalogo.exception.RegraDeNegocioException;
import br.com.catalogo.repository.CategoriaRepository;
import br.com.catalogo.repository.ProdutoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio das categorias.
 *
 * <p>É aqui — e não no controller nem na entidade — que moram as regras que
 * dependem de consultar o repositório: unicidade de nome e proibição de excluir
 * categoria em uso. A classe não conhece HTTP; sinaliza problemas por exceções de
 * domínio que a camada de borda traduz em status code.
 *
 * <p>{@code @Transactional(readOnly = true)} na classe com sobrescrita nos métodos
 * de escrita: o padrão seguro é leitura, e cada método que grava declara sua
 * intenção explicitamente.
 */
@Service
@Transactional(readOnly = true)
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final ProdutoRepository produtoRepository;

    /**
     * Injeção por construtor, sem {@code @Autowired} em campo: deixa as dependências
     * explícitas, permite campos {@code final} e torna a classe instanciável em um
     * teste unitário sem subir contexto do Spring.
     */
    public CategoriaService(CategoriaRepository categoriaRepository, ProdutoRepository produtoRepository) {
        this.categoriaRepository = categoriaRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional
    public Categoria criar(CategoriaRequest requisicao) {
        String nome = requisicao.nome().trim();
        if (categoriaRepository.existsByNomeIgnoreCase(nome)) {
            throw new RegraDeNegocioException("Já existe uma categoria com o nome '%s'.".formatted(nome));
        }
        return categoriaRepository.save(new Categoria(nome, requisicao.descricao()));
    }

    public Categoria buscarPorId(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Categoria", id));
    }

    public Page<Categoria> listar(Pageable paginacao) {
        return categoriaRepository.findAll(paginacao);
    }

    @Transactional
    public Categoria atualizar(Long id, CategoriaRequest requisicao) {
        Categoria categoria = buscarPorId(id);
        String nome = requisicao.nome().trim();
        // 'AndIdNot': renomear a categoria para o nome que ela já tem não é conflito.
        if (categoriaRepository.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new RegraDeNegocioException("Já existe uma categoria com o nome '%s'.".formatted(nome));
        }
        categoria.atualizar(nome, requisicao.descricao());
        return categoria;
    }

    @Transactional
    public void excluir(Long id) {
        Categoria categoria = buscarPorId(id);
        // Exclusão restritiva em vez de cascata: apagar a categoria levaria junto
        // produtos e seus históricos, silenciosamente (ver README, decisão 11).
        if (produtoRepository.existsByCategoriaId(id)) {
            throw new RegraDeNegocioException(
                    "A categoria '%s' não pode ser excluída porque possui produtos vinculados."
                            .formatted(categoria.getNome()));
        }
        categoriaRepository.delete(categoria);
    }
}
