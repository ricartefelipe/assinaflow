package br.com.ricarte.assinaflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration.class)
public class AssinaFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(AssinaFlowApplication.class, args);
    }
}
