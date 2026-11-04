package br.com.ricarte.assinaflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Rotas que respondem sem login recebem de tudo da internet. Corpo vazio, JSON inválido, campos faltando, método ou
 * tipo de conteúdo errado e rota inexistente são erro de quem chamou: 4xx, nunca 500.
 */
@TestPropertySource(properties = "app.security.enabled=true")
class PublicRouteInputIntegrationTest extends IntegrationTestBase {
    @Autowired MockMvc mvc;

    private static final String[] PUBLIC_POSTS = {"/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/users"};

    @Test
    void publicPostsRejectBadInputWithAClientError() throws Exception {
        for (String path : PUBLIC_POSTS) {
            clientError(post(path));
            clientError(post(path).contentType(MediaType.APPLICATION_JSON));
            clientError(post(path).contentType(MediaType.APPLICATION_JSON).content("{"));
            clientError(post(path).contentType(MediaType.APPLICATION_JSON).content("[]"));
            clientError(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"));
            clientError(post(path).contentType(MediaType.TEXT_PLAIN).content("x"));
            clientError(put(path).contentType(MediaType.APPLICATION_JSON).content("{}"));
        }
    }

    @Test
    void stripeWebhookDoesNotExistWithTheSimulatedGateway() throws Exception {
        mvc.perform(post("/api/v1/billing/stripe/webhook")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/billing/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownRoutesAreClientErrors() throws Exception {
        // Fora das rotas públicas, a rota desconhecida pede login (401) e não revela se existe.
        mvc.perform(post("/api/v1/auth/unknown").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/plans/unknown/deeper")).andExpect(status().is4xxClientError());
        mvc.perform(get("/internal/v1/totalrecall/unknown")).andExpect(status().isNotFound());
        mvc.perform(post("/internal/v1/totalrecall/users")).andExpect(status().is4xxClientError());
        mvc.perform(post("/internal/v1/totalrecall/users").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void unexpectedErrorsDoNotLeakDetails() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed JSON"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    private void clientError(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request).andExpect(status().is4xxClientError());
    }
}
