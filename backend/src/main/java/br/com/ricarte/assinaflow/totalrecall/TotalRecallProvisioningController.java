package br.com.ricarte.assinaflow.totalrecall;

import br.com.ricarte.assinaflow.totalrecall.dto.TotalRecallProvisionRequest;
import br.com.ricarte.assinaflow.totalrecall.dto.TotalRecallProvisionResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/totalrecall")
public class TotalRecallProvisioningController {

    private final TotalRecallProvisioningService provisioningService;
    private final String provisionToken;

    public TotalRecallProvisioningController(
            TotalRecallProvisioningService provisioningService,
            @Value("${app.totalrecall.provision-token:}") String provisionToken
    ) {
        this.provisioningService = provisioningService;
        this.provisionToken = provisionToken;
    }

    @PostMapping("/users")
    public TotalRecallProvisionResponse provision(
            @RequestHeader(value = "X-TotalRecall-Token", required = false) String token,
            @Valid @RequestBody TotalRecallProvisionRequest request
    ) {
        authorize(token);
        return provisioningService.provision(request);
    }

    @GetMapping("/health")
    public Map<String, Boolean> health(
            @RequestHeader(value = "X-TotalRecall-Token", required = false) String token
    ) {
        authorize(token);
        return Map.of("ok", true);
    }

    private void authorize(String token) {
        if (provisionToken == null || provisionToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Provisionamento indisponivel.");
        }
        if (token == null || !MessageDigest.isEqual(
                provisionToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de provisionamento invalido.");
        }
    }
}
