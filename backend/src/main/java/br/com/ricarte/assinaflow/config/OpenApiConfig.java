package br.com.ricarte.assinaflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AssinaFlow API")
                        .version("1.0.0")
                        .description("""
                                API de gestao de assinaturas com renovacao automatica e cancelamento no fim do ciclo.

                                Estados relevantes:
                                - ATIVA: assinatura vigente com renovacao conforme autoRenew
                                - CANCELAMENTO_AGENDADO: acesso permanece ate dataExpiracao; nao renova
                                - CANCELADA: ciclo encerrado
                                - SUSPENSA: falha reiterada de cobranca
                                """)
                        .license(new License().name("Proprietary")));
    }
}
