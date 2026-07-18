package br.com.ricarte.assinaflow.auth;

import br.com.ricarte.assinaflow.auth.dto.AuthResponse;
import br.com.ricarte.assinaflow.auth.dto.LoginRequest;
import br.com.ricarte.assinaflow.auth.dto.RegisterRequest;
import br.com.ricarte.assinaflow.common.exception.ConflictException;
import br.com.ricarte.assinaflow.common.exception.UnauthorizedException;
import br.com.ricarte.assinaflow.user.PaymentBehavior;
import br.com.ricarte.assinaflow.user.PaymentProfileEntity;
import br.com.ricarte.assinaflow.user.PaymentProfileRepository;
import br.com.ricarte.assinaflow.user.UserEntity;
import br.com.ricarte.assinaflow.user.UserRepository;
import br.com.ricarte.assinaflow.user.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PaymentProfileRepository paymentProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PaymentProfileRepository paymentProfileRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.paymentProfileRepository = paymentProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmailIgnoreCase(req.getEmail())) {
            throw new ConflictException("USER_EMAIL_ALREADY_EXISTS", "Email ja cadastrado.");
        }

        UserEntity user = new UserEntity();
        user.setEmail(req.getEmail());
        user.setNome(req.getNome());
        user.setPasswordHash(passwordEncoder.encode(req.getSenha()));
        user = userRepository.save(user);

        PaymentProfileEntity profile = new PaymentProfileEntity();
        profile.setUser(user);
        profile.setBehavior(PaymentBehavior.ALWAYS_APPROVE);
        profile.setFailNextN(0);
        paymentProfileRepository.save(profile);

        return toAuthResponse(user, profile);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        UserEntity user = userRepository.findByEmailIgnoreCase(req.getEmail())
                .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", "Email ou senha invalidos."));

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Email ou senha invalidos.");
        }
        if (!passwordEncoder.matches(req.getSenha(), user.getPasswordHash())) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Email ou senha invalidos.");
        }

        PaymentProfileEntity profile = paymentProfileRepository.findById(user.getId()).orElse(null);
        return toAuthResponse(user, profile);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("INVALID_TOKEN", "Sessao invalida."));
        PaymentProfileEntity profile = paymentProfileRepository.findById(userId).orElse(null);
        return toUserResponse(user, profile);
    }

    private AuthResponse toAuthResponse(UserEntity user, PaymentProfileEntity profile) {
        String token = jwtService.createToken(user.getId(), user.getEmail());
        return new AuthResponse(token, toUserResponse(user, profile));
    }

    private static UserResponse toUserResponse(UserEntity user, PaymentProfileEntity profile) {
        UserResponse res = new UserResponse();
        res.setId(user.getId());
        res.setEmail(user.getEmail());
        res.setNome(user.getNome());
        if (profile != null) {
            res.setPaymentBehavior(profile.getBehavior());
            res.setPaymentFailNextN(profile.getFailNextN());
        } else {
            res.setPaymentBehavior(PaymentBehavior.ALWAYS_APPROVE);
            res.setPaymentFailNextN(0);
        }
        return res;
    }
}
