package br.com.ricarte.assinaflow.totalrecall;

import br.com.ricarte.assinaflow.common.exception.BadRequestException;
import br.com.ricarte.assinaflow.common.exception.NotFoundException;
import br.com.ricarte.assinaflow.totalrecall.dto.TotalRecallProvisionRequest;
import br.com.ricarte.assinaflow.totalrecall.dto.TotalRecallProvisionResponse;
import br.com.ricarte.assinaflow.user.PaymentBehavior;
import br.com.ricarte.assinaflow.user.PaymentProfileEntity;
import br.com.ricarte.assinaflow.user.PaymentProfileRepository;
import br.com.ricarte.assinaflow.user.UserEntity;
import br.com.ricarte.assinaflow.user.UserRepository;
import br.com.ricarte.assinaflow.user.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class TotalRecallProvisioningService {

    private final UserRepository userRepository;
    private final PaymentProfileRepository paymentProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public TotalRecallProvisioningService(
            UserRepository userRepository,
            PaymentProfileRepository paymentProfileRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.paymentProfileRepository = paymentProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public TotalRecallProvisionResponse provision(TotalRecallProvisionRequest request) {
        return switch (normalizedAction(request.getAction())) {
            case "upsert" -> upsert(request);
            case "disable", "revoke" -> disable(request);
            default -> throw new BadRequestException("INVALID_ACTION", "Acao invalida.");
        };
    }

    private TotalRecallProvisionResponse upsert(TotalRecallProvisionRequest request) {
        validateUserRole(request.getRole());
        UserEntity user = userRepository.findByEmailIgnoreCase(request.getEmail()).orElse(null);
        boolean created = user == null;
        if (user == null) {
            if (isBlank(request.getName()) || isBlank(request.getPassword())) {
                throw new BadRequestException("USER_DETAILS_REQUIRED", "Nome e senha sao obrigatorios para criar usuario.");
            }
            user = new UserEntity();
            user.setEmail(request.getEmail());
        }

        if (!isBlank(request.getName())) {
            user.setNome(request.getName());
        }
        if (!isBlank(request.getPassword())) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        if (request.getExpiresAt() != null) {
            user.setExpiresAt(request.getExpiresAt());
        }
        userRepository.save(user);
        if (created) {
            createPaymentProfile(user);
        }

        return response(user);
    }

    private TotalRecallProvisionResponse disable(TotalRecallProvisionRequest request) {
        UserEntity user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Usuario nao encontrado."));
        user.setEnabled(false);
        userRepository.save(user);
        return response(user);
    }

    private void createPaymentProfile(UserEntity user) {
        PaymentProfileEntity profile = new PaymentProfileEntity();
        profile.setUser(user);
        profile.setBehavior(PaymentBehavior.ALWAYS_APPROVE);
        profile.setFailNextN(0);
        paymentProfileRepository.save(profile);
    }

    private static void validateUserRole(String role) {
        if (role != null && !role.equalsIgnoreCase(UserRole.USER.name())) {
            throw new BadRequestException("INVALID_ROLE", "Role invalida.");
        }
    }

    private static String normalizedAction(String action) {
        return action == null ? "" : action.toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static TotalRecallProvisionResponse response(UserEntity user) {
        return new TotalRecallProvisionResponse(true, user.getEmail(), user.getRole(), user.isEnabled());
    }
}
