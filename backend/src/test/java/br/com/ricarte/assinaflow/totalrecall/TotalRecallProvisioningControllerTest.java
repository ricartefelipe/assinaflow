package br.com.ricarte.assinaflow.totalrecall;

import br.com.ricarte.assinaflow.common.exception.GlobalExceptionHandler;
import br.com.ricarte.assinaflow.totalrecall.dto.TotalRecallProvisionResponse;
import br.com.ricarte.assinaflow.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TotalRecallProvisioningControllerTest {

    private TotalRecallProvisioningService provisioningService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        provisioningService = mock(TotalRecallProvisioningService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TotalRecallProvisioningController(provisioningService, "secret"))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void healthShouldRequireProvisioningToken() throws Exception {
        mockMvc.perform(get("/internal/v1/totalrecall/health")
                        .header("X-TotalRecall-Token", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void upsertShouldAcceptProvisioningToken() throws Exception {
        when(provisioningService.provision(any())).thenReturn(
                new TotalRecallProvisionResponse(true, "user@example.com", UserRole.USER, true)
        );

        mockMvc.perform(post("/internal/v1/totalrecall/users")
                        .header("X-TotalRecall-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "name": "User",
                                  "password": "senha12345",
                                  "action": "upsert"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.enabled").value(true));
    }
}
