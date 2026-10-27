package br.com.catalogo.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Quem pode chamar o quê.
 *
 * <p>A API é um <b>resource server</b> OAuth 2.0: não tem login nem emite tokens.
 * O cliente obtém um JWT de um provedor externo (Keycloak, Auth0, Cognito...) e o
 * envia em {@code Authorization: Bearer <token>}. O Spring Security confere a
 * assinatura com as chaves públicas do provedor, o emissor ({@code iss}), a
 * validade ({@code exp}) e a audiência ({@code aud}), tudo configurado em
 * {@code spring.security.oauth2.resourceserver.jwt} no {@code application.yml}.
 *
 * <p><b>As regras:</b> consultar o catálogo ({@code GET}) é público; cadastrar,
 * alterar e excluir exigem token. Health check e documentação ficam abertos.
 * Todo o resto cai no {@code anyRequest().authenticated()}: uma rota nova nasce
 * protegida, e abri-la é uma decisão explícita, nunca um esquecimento.
 */
@Configuration
public class SegurancaConfig {

    private final String basePath;

    public SegurancaConfig(@Value("${api.base-path:/api/v1}") String basePath) {
        this.basePath = basePath;
    }

    @Bean
    public SecurityFilterChain filtroDeSeguranca(HttpSecurity http,
                                                 @Qualifier("handlerExceptionResolver")
                                                 HandlerExceptionResolver resolvedorDeErros) throws Exception {
        http
                // CSRF protege sessões guardadas em cookie. Aqui não há sessão nem
                // cookie: cada requisição traz o próprio token.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers(HttpMethod.GET, basePath + "/**").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(servidor -> servidor
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(naoAutenticado(resolvedorDeErros))
                        .accessDeniedHandler(acessoNegado(resolvedorDeErros)));
        return http.build();
    }

    /**
     * Os erros de segurança acontecem em um filtro, antes de a requisição chegar
     * ao Spring MVC, e por isso o {@code TratadorGlobalDeErros} não os veria.
     * Repassá-los ao {@code HandlerExceptionResolver} do MVC faz com que saiam no
     * mesmo formato RFC 9457 de todos os outros erros da API.
     *
     * <p>Antes disso, o handler padrão do Spring escreve o cabeçalho
     * {@code WWW-Authenticate: Bearer ...}, que a especificação do OAuth exige
     * no {@code 401} e que diz ao cliente o motivo (token ausente, expirado...).
     */
    private AuthenticationEntryPoint naoAutenticado(HandlerExceptionResolver resolvedorDeErros) {
        BearerTokenAuthenticationEntryPoint padrao = new BearerTokenAuthenticationEntryPoint();
        return (requisicao, resposta, excecao) -> {
            padrao.commence(requisicao, resposta, excecao);
            resolvedorDeErros.resolveException(requisicao, resposta, null, excecao);
        };
    }

    private AccessDeniedHandler acessoNegado(HandlerExceptionResolver resolvedorDeErros) {
        BearerTokenAccessDeniedHandler padrao = new BearerTokenAccessDeniedHandler();
        return (requisicao, resposta, excecao) -> {
            padrao.handle(requisicao, resposta, excecao);
            resolvedorDeErros.resolveException(requisicao, resposta, null, excecao);
        };
    }
}
