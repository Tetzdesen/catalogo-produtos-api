package br.com.catalogo.service;

import br.com.catalogo.dto.PaginaResponse;
import br.com.catalogo.dto.ProdutoRequest;
import br.com.catalogo.dto.ProdutoResponse;
import org.springframework.data.domain.Pageable;

/**
 * O que o catálogo oferece a quem está na borda HTTP: o contrato, sem a
 * implementação.
 *
 * <p><b>Por que uma interface com uma única implementação?</b> Ela é o "D" de
 * SOLID, a inversão de dependência: o {@code ProdutoController} depende desta
 * abstração, não de {@link ProdutoServiceImpl}. Quem está acima não conhece os
 * detalhes de quem está abaixo (transação, regras, mapper, repositório).
 *
 * <p>Com uma só implementação, parte disso é convenção. A interface passa a se
 * pagar de verdade quando aparece uma segunda implementação, um decorador (por
 * exemplo, um que meça o tempo de cada chamada) ou uma fronteira entre módulos.
 * Ela também resume em cinco linhas tudo o que a camada faz, e é por aqui que
 * vale começar a ler o service.
 */
public interface ProdutoService {

    /** Cadastra um produto, que nasce ativo. */
    ProdutoResponse criar(ProdutoRequest dados);

    /** Lança {@code ProdutoNaoEncontradoException} se o id não existir. */
    ProdutoResponse buscar(Long id);

    /** Lista paginada; {@code nome} nulo ou em branco significa "sem filtro". */
    PaginaResponse<ProdutoResponse> listar(String nome, Pageable paginacao);

    /** Substitui todos os dados do produto, inclusive {@code ativo}. */
    ProdutoResponse atualizar(Long id, ProdutoRequest dados);

    /** Exclui definitivamente. */
    void excluir(Long id);
}
