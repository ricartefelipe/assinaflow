package br.com.ricarte.assinaflow.user.dto;

import br.com.ricarte.assinaflow.user.PaymentBehavior;

import java.util.UUID;

public class UserResponse {
    private UUID id;
    private String email;
    private String nome;
    private PaymentBehavior paymentBehavior;
    private int paymentFailNextN;

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNome() {
        return nome;
    }

    public PaymentBehavior getPaymentBehavior() {
        return paymentBehavior;
    }

    public int getPaymentFailNextN() {
        return paymentFailNextN;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public void setPaymentBehavior(PaymentBehavior paymentBehavior) {
        this.paymentBehavior = paymentBehavior;
    }

    public void setPaymentFailNextN(int paymentFailNextN) {
        this.paymentFailNextN = paymentFailNextN;
    }
}
