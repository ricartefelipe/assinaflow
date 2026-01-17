package br.com.ricarte.assinaflow.user;

import br.com.ricarte.assinaflow.user.dto.CreateUserRequest;
import br.com.ricarte.assinaflow.user.dto.PaymentProfileRequest;
import br.com.ricarte.assinaflow.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest req) {
        UserResponse created = userService.create(req);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.getId())).body(created);
    }

    @GetMapping("/{userId}")
    public UserResponse get(@PathVariable UUID userId) {
        return userService.get(userId);
    }

    @PutMapping("/{userId}/payment-profile")
    public UserResponse updatePaymentProfile(@PathVariable UUID userId, @Valid @RequestBody PaymentProfileRequest req) {
        return userService.updatePaymentProfile(userId, req);
    }
}
