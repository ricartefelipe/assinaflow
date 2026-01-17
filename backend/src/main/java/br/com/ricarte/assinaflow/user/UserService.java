package br.com.ricarte.assinaflow.user;

import br.com.ricarte.assinaflow.common.exception.ConflictException;
import br.com.ricarte.assinaflow.common.exception.NotFoundException;
import br.com.ricarte.assinaflow.user.dto.CreateUserRequest;
import br.com.ricarte.assinaflow.user.dto.PaymentProfileRequest;
import br.com.ricarte.assinaflow.user.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PaymentProfileRepository paymentProfileRepository;

    public UserService(UserRepository userRepository, PaymentProfileRepository paymentProfileRepository) {
        this.userRepository = userRepository;
        this.paymentProfileRepository = paymentProfileRepository;
    }

    @Transactional
    public UserResponse create(CreateUserRequest req) {
        if (userRepository.existsByEmailIgnoreCase(req.getEmail())) {
            throw new ConflictException("USER_EMAIL_ALREADY_EXISTS", "Email ja cadastrado.");
        }

        UserEntity user = new UserEntity();
        user.setEmail(req.getEmail());
        user.setNome(req.getNome());
        user = userRepository.save(user);

        PaymentProfileRequest pr = req.getPaymentProfile();
        PaymentProfileEntity profile = new PaymentProfileEntity();
        profile.setUser(user);
        profile.setBehavior(pr != null ? pr.getBehavior() : PaymentBehavior.ALWAYS_APPROVE);
        profile.setFailNextN(pr != null ? pr.getFailNextN() : 0);
        paymentProfileRepository.save(profile);

        return toResponse(user, profile);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Usuario nao encontrado."));

        PaymentProfileEntity profile = paymentProfileRepository.findById(userId).orElse(null);
        return toResponse(user, profile);
    }

    @Transactional
    public UserResponse updatePaymentProfile(UUID userId, PaymentProfileRequest req) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Usuario nao encontrado."));

        PaymentProfileEntity profile = paymentProfileRepository.findById(userId).orElseGet(() -> {
            PaymentProfileEntity p = new PaymentProfileEntity();
            p.setUser(user);
            return p;
        });

        profile.setBehavior(req.getBehavior());
        profile.setFailNextN(req.getFailNextN());
        profile = paymentProfileRepository.save(profile);

        return toResponse(user, profile);
    }

    private static UserResponse toResponse(UserEntity user, PaymentProfileEntity profile) {
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
