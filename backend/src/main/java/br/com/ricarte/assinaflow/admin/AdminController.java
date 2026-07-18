package br.com.ricarte.assinaflow.admin;

import br.com.ricarte.assinaflow.outbox.OutboxEventEntity;
import br.com.ricarte.assinaflow.outbox.OutboxStatus;
import br.com.ricarte.assinaflow.subscription.dto.SubscriptionResponse;
import br.com.ricarte.assinaflow.user.dto.PaymentProfileRequest;
import br.com.ricarte.assinaflow.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    @Operation(summary = "Lista usuarios")
    public List<UserResponse> listUsers(@RequestParam(defaultValue = "50") int limit) {
        return adminService.listUsers(limit);
    }

    @GetMapping("/subscriptions")
    @Operation(summary = "Lista assinaturas recentes")
    public List<SubscriptionResponse> listSubscriptions(@RequestParam(defaultValue = "50") int limit) {
        return adminService.listSubscriptions(limit);
    }

    @GetMapping("/outbox")
    @Operation(summary = "Lista eventos de outbox por status")
    public List<OutboxEventEntity> listOutbox(
            @RequestParam(defaultValue = "DEAD") OutboxStatus status,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return adminService.listOutbox(status, limit);
    }

    @PostMapping("/outbox/{id}/requeue")
    @Operation(summary = "Reenfileira evento DEAD/PENDING")
    public OutboxEventEntity requeue(@PathVariable UUID id) {
        return adminService.requeueOutbox(id);
    }

    @PutMapping("/users/{userId}/payment-profile")
    @Operation(summary = "Atualiza payment profile de um usuario")
    public UserResponse updatePaymentProfile(
            @PathVariable UUID userId,
            @Valid @RequestBody PaymentProfileRequest req
    ) {
        return adminService.updatePaymentProfile(userId, req);
    }
}
