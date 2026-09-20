package br.com.catalogo.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

/**
 * Atalho para os testes que exercitam a API de ponta a ponta.
 *
 * <p>Sobe o contexto inteiro e expõe o {@code MockMvc}. A diferença para os testes
 * unitários de service é o que está sendo provado aqui: serialização JSON, códigos
 * de status, cabeçalhos, validação de entrada e o formato RFC 9457 dos erros —
 * tudo que só existe quando o stack HTTP está de pé.
 *
 * <p>Sem {@code @Transactional}: a limpeza entre testes é feita explicitamente,
 * para que o comportamento transacional real da aplicação (commit incluído) seja
 * o que está sob teste.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public @interface TesteDeIntegracao {
}
