package br.com.ricarte.assinaflow.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class TotalRecallClient {

    private static final Logger log = LoggerFactory.getLogger(TotalRecallClient.class);

    private final RestClient restClient;
    private final boolean enabled;
    private final String systemSlug;

    public TotalRecallClient(
            @Value("${app.totalrecall.base-url:http://54.94.163.136:9087}") String baseUrl,
            @Value("${app.totalrecall.enabled:true}") boolean enabled,
            @Value("${app.totalrecall.system-slug:assinaflow}") String systemSlug
    ) {
        this.enabled = enabled;
        this.systemSlug = systemSlug;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl.replaceAll("/$", ""))
                .build();
    }

    public boolean validatePassword(String email, String password) {
        if (!enabled || email == null || password == null) {
            return false;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.post()
                    .uri("/api/v1/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "email", email,
                            "password", password,
                            "system", systemSlug
                    ))
                    .retrieve()
                    .body(Map.class);
            return body != null && Boolean.TRUE.equals(body.get("valid"));
        } catch (Exception ex) {
            log.warn("TotalRecall login failed: {}", ex.getMessage());
            return false;
        }
    }
}
