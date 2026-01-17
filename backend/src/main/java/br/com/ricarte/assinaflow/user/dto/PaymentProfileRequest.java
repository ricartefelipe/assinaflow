package br.com.ricarte.assinaflow.user.dto;

import br.com.ricarte.assinaflow.user.PaymentBehavior;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class PaymentProfileRequest {

    @NotNull
    private PaymentBehavior behavior = PaymentBehavior.ALWAYS_APPROVE;

    @Min(0)
    private int failNextN = 0;

    public PaymentBehavior getBehavior() {
        return behavior;
    }

    public void setBehavior(PaymentBehavior behavior) {
        this.behavior = behavior;
    }

    public int getFailNextN() {
        return failNextN;
    }

    public void setFailNextN(int failNextN) {
        this.failNextN = failNextN;
    }
}
