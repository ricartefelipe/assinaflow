package br.com.ricarte.assinaflow.payment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class PaymentConfig {

    @Bean
    RestClient.Builder restClientBuilder() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        return RestClient.builder().requestFactory(factory);
    }
}
