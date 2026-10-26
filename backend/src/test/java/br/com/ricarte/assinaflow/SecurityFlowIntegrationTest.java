package br.com.ricarte.assinaflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = "app.security.enabled=true")
class SecurityFlowIntegrationTest extends IntegrationTestBase {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired ObjectMapper mapper;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired br.com.ricarte.assinaflow.user.UserRepository users;

    @Test
    void realLoginAllowsOwnSubscriptionButDeniesOtherUserAndAdmin() throws Exception {
        String email = "security-" + UUID.randomUUID() + "@example.test";
        String credentials = "{\"email\":\"" + email + "\",\"nome\":\"Synthetic\",\"senha\":\"synthetic-password\"}";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isCreated());
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk()).andReturn();
        var json = mapper.readTree(response.getResponse().getContentAsString());
        String id = json.path("user").path("id").asText();
        String bearer = "Bearer " + json.path("accessToken").asText();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/users/{id}/subscriptions", id).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"plano\":\"BASICO\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/users/{id}/subscriptions/active", id).header("Authorization", bearer))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/{id}/payments", id).header("Authorization", bearer)).andExpect(status().isOk());
        UUID payment = jdbc.queryForObject("SELECT id FROM payment_obligations WHERE user_id=?", UUID.class, UUID.fromString(id));
        mvc.perform(get("/api/v1/users/{id}/payments/{payment}", id, payment).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/{id}/payments/{payment}", id, UUID.randomUUID()).header("Authorization", bearer)).andExpect(status().isNotFound());
        String other = UUID.randomUUID().toString();
        mvc.perform(get("/api/v1/users/{id}/payments", other).header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/{id}/payments/{payment}", other, payment).header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/{id}/subscriptions/change-plan", other).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"plano\":\"PREMIUM\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/{id}/subscriptions/active", other).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/{id}/subscriptions/cancel", other).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/{id}", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer + "invalid")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/users/{id}/subscriptions/cancel", id).header("Authorization", bearer))
                .andExpect(status().isOk());
        var identity = users.findById(UUID.fromString(id)).orElseThrow();
        identity.setRole(br.com.ricarte.assinaflow.user.UserRole.ADMIN);
        users.saveAndFlush(identity);
        mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer)).andExpect(status().isOk());
        identity.setRole(br.com.ricarte.assinaflow.user.UserRole.USER);
        users.saveAndFlush(identity);
        mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer)).andExpect(status().isForbidden());
        identity.setEnabled(false);
        users.saveAndFlush(identity);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mvc.perform(options("/api/v1/auth/me").header("Origin", "http://untrusted.test")
                .header("Access-Control-Request-Method", "GET")).andExpect(status().isForbidden());
    }
    @Test
    void apiHasNoXsltViewsOrSseFragmentEndpoints() {
        org.assertj.core.api.Assertions.assertThat(context.getBeansOfType(
                org.springframework.web.servlet.view.xslt.XsltViewResolver.class)).isEmpty();
        var handlers=context.getBean("requestMappingHandlerMapping", org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
        handlers.getHandlerMethods().values().stream()
                .filter(method -> method.getBeanType().getPackageName().startsWith("br.com.ricarte.assinaflow"))
                .forEach(method -> org.assertj.core.api.Assertions.assertThat(method.getMethod().getGenericReturnType().getTypeName())
                        .doesNotContain("SseEmitter", "FragmentsRendering", "Flux<"));
    }

}
