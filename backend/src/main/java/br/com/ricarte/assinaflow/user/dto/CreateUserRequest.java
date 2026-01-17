package br.com.ricarte.assinaflow.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateUserRequest {

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    @NotBlank
    @Size(max = 120)
    private String nome;

    @Valid
    private PaymentProfileRequest paymentProfile;

    public String getEmail() {
        return email;
    }

    public String getNome() {
        return nome;
    }

    public PaymentProfileRequest getPaymentProfile() {
        return paymentProfile;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public void setPaymentProfile(PaymentProfileRequest paymentProfile) {
        this.paymentProfile = paymentProfile;
    }
}
