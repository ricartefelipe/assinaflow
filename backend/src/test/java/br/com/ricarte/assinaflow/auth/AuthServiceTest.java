package br.com.ricarte.assinaflow.auth;

import br.com.ricarte.assinaflow.auth.dto.LoginRequest;
import br.com.ricarte.assinaflow.auth.dto.RegisterRequest;
import br.com.ricarte.assinaflow.common.exception.ConflictException;
import br.com.ricarte.assinaflow.common.exception.UnauthorizedException;
import br.com.ricarte.assinaflow.user.PaymentProfileRepository;
import br.com.ricarte.assinaflow.user.UserEntity;
import br.com.ricarte.assinaflow.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PaymentProfileRepository paymentProfileRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    @InjectMocks
    AuthService authService;

    @Test
    void registerShouldRejectDuplicateEmail() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("a@example.com");
        req.setNome("A");
        req.setSenha("senha12345");
        when(userRepository.existsByEmailIgnoreCase("a@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void loginShouldRejectWrongPassword() {
        UUID id = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail("a@example.com");
        user.setNome("A");
        user.setPasswordHash("hash");

        LoginRequest req = new LoginRequest();
        req.setEmail("a@example.com");
        req.setSenha("senha12345");

        when(userRepository.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha12345", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void registerShouldCreateUserAndToken() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("a@example.com");
        req.setNome("A");
        req.setSenha("senha12345");

        when(userRepository.existsByEmailIgnoreCase("a@example.com")).thenReturn(false);
        when(passwordEncoder.encode("senha12345")).thenReturn("hash");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(paymentProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.createToken(any(UUID.class), any(), any())).thenReturn("token");

        var res = authService.register(req);

        assertThat(res.getAccessToken()).isEqualTo("token");
        assertThat(res.getUser().getEmail()).isEqualTo("a@example.com");
        verify(paymentProfileRepository).save(any());
    }
}
