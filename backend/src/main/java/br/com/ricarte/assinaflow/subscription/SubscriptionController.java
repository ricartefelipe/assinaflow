package br.com.ricarte.assinaflow.subscription;

import br.com.ricarte.assinaflow.subscription.dto.CreateSubscriptionRequest;
import br.com.ricarte.assinaflow.subscription.dto.SubscriptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}/subscriptions")
@Tag(name = "Assinaturas")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    @Operation(summary = "Cria assinatura", description = "No maximo uma assinatura ATIVA ou CANCELAMENTO_AGENDADO por usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Assinatura criada"),
            @ApiResponse(responseCode = "404", description = "Usuario nao encontrado"),
            @ApiResponse(responseCode = "409", description = "Ja existe assinatura ativa")
    })
    public ResponseEntity<SubscriptionResponse> create(@PathVariable UUID userId, @Valid @RequestBody CreateSubscriptionRequest req) {
        SubscriptionResponse created = subscriptionService.create(userId, req);
        return ResponseEntity
                .created(URI.create("/api/v1/users/" + userId + "/subscriptions/" + created.getId()))
                .body(created);
    }

    @GetMapping("/active")
    @Operation(
            summary = "Consulta assinatura ativa",
            description = "Inclui ATIVA e CANCELAMENTO_AGENDADO. Em CANCELAMENTO_AGENDADO o acesso permanece ate dataExpiracao."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assinatura encontrada"),
            @ApiResponse(responseCode = "204", description = "Usuario sem assinatura ativa"),
            @ApiResponse(responseCode = "404", description = "Usuario nao encontrado")
    })
    public ResponseEntity<SubscriptionResponse> getActive(@PathVariable UUID userId) {
        SubscriptionResponse active = subscriptionService.getActive(userId);
        if (active == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(active);
    }

    @GetMapping("/{subscriptionId}")
    @Operation(summary = "Consulta assinatura por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assinatura encontrada"),
            @ApiResponse(responseCode = "404", description = "Usuario ou assinatura nao encontrados")
    })
    public SubscriptionResponse getById(@PathVariable UUID userId, @PathVariable UUID subscriptionId) {
        return subscriptionService.getById(userId, subscriptionId);
    }

    @GetMapping
    @Operation(summary = "Historico de assinaturas do usuario")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista (pode ser vazia)"),
            @ApiResponse(responseCode = "404", description = "Usuario nao encontrado")
    })
    public List<SubscriptionResponse> history(@PathVariable UUID userId) {
        return subscriptionService.history(userId);
    }

    @PostMapping("/cancel")
    @Operation(
            summary = "Agenda cancelamento",
            description = "Nao corta o acesso imediatamente. Status vira CANCELAMENTO_AGENDADO e autoRenew fica false."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelamento agendado"),
            @ApiResponse(responseCode = "404", description = "Assinatura ativa nao encontrada")
    })
    public SubscriptionResponse cancel(@PathVariable UUID userId) {
        return subscriptionService.cancel(userId);
    }
}
