package br.com.ricarte.assinaflow.testutil;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestTimeProviderConfig {

    @Bean
    @Primary
    public MutableTimeProvider timeProvider() {
        return new MutableTimeProvider();
    }
}
