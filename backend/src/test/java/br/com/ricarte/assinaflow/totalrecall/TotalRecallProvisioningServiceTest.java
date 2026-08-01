package br.com.ricarte.assinaflow.totalrecall;

import br.com.ricarte.assinaflow.totalrecall.dto.TotalRecallProvisionRequest;
import br.com.ricarte.assinaflow.user.PaymentProfileRepository;
import br.com.ricarte.assinaflow.user.UserEntity;
import br.com.ricarte.assinaflow.user.UserRepository;
import br.com.ricarte.assinaflow.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TotalRecallProvisioningServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PaymentProfileRepository paymentProfileRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Test
    void upsertShouldCreateEnabledUserWithExpiration() {
        TotalRecallProvisioningService service = new TotalRecallProvisioningService(
                userRepository,
                paymentProfileRepository,
                passwordEncoder
        );
        TotalRecallProvisionRequest request = new TotalRecallProvisionRequest();
        request.setEmail("user@example.com");
        request.setName("TotalRecall User");
        request.setPassword("senha12345");
        request.setRole("user");
        request.setExpiresAt(Instant.parse("2026-12-31T23:59:59Z"));
        request.setAction("upsert");

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha12345")).thenReturn("hash");

        var response = service.provision(request);

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        UserEntity user = userCaptor.getValue();
        assertThat(user.getNome()).isEqualTo("TotalRecall User");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getExpiresAt()).isEqualTo(Instant.parse("2026-12-31T23:59:59Z"));
        assertThat(response.enabled()).isTrue();
    }

    @Test
    void disableShouldBlockExistingUser() {
        TotalRecallProvisioningService service = new TotalRecallProvisioningService(
                userRepository,
                paymentProfileRepository,
                passwordEncoder
        );
        TotalRecallProvisionRequest request = new TotalRecallProvisionRequest();
        request.setEmail("user@example.com");
        request.setAction("disable");
        UserEntity user = new UserEntity();
        user.setEmail("user@example.com");

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        var response = service.provision(request);

        verify(userRepository).save(user);
        assertThat(user.isEnabled()).isFalse();
        assertThat(response.enabled()).isFalse();
    }
}
