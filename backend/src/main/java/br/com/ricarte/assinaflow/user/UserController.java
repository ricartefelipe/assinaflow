package br.com.ricarte.assinaflow.user;

import br.com.ricarte.assinaflow.user.dto.CreateUserRequest;
import br.com.ricarte.assinaflow.user.dto.PaymentProfileRequest;
import br.com.ricarte.assinaflow.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Usuarios")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @Operation(summary = "Cria usuario")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario criado"),
            @ApiResponse(responseCode = "400", description = "Payload invalido"),
            @ApiResponse(responseCode = "409", description = "Email ja cadastrado")
    })
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest req) {
        UserResponse created = userService.create(req);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.getId())).body(created);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Consulta usuario")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
            @ApiResponse(responseCode = "404", description = "Usuario nao encontrado")
    })
    public UserResponse get(@PathVariable UUID userId) {
        return userService.get(userId);
    }

    @PutMapping("/{userId}/payment-profile")
    @Operation(summary = "Atualiza perfil de pagamento simulado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil atualizado"),
            @ApiResponse(responseCode = "404", description = "Usuario nao encontrado")
    })
    public UserResponse updatePaymentProfile(@PathVariable UUID userId, @Valid @RequestBody PaymentProfileRequest req) {
        return userService.updatePaymentProfile(userId, req);
    }
}
