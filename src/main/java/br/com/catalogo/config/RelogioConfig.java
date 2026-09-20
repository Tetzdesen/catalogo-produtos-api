package br.com.catalogo.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Disponibiliza o relógio como um bean.
 *
 * <p>Chamar {@code Instant.now()} espalhado pelo código torna impossível testar
 * comportamento dependente de tempo sem recorrer a truques. Com o {@link Clock}
 * injetado, um teste passa um relógio fixo e o resto do código não muda.
 */
@Configuration
public class RelogioConfig {

    @Bean
    public Clock relogio() {
        return Clock.systemUTC();
    }
}
