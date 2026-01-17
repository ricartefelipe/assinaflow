package br.com.ricarte.assinaflow.subscription;

public enum Plan {
    BASICO(1990),
    PREMIUM(3990),
    FAMILIA(5990);

    private final int priceCents;

    Plan(int priceCents) {
        this.priceCents = priceCents;
    }

    public int getPriceCents() {
        return priceCents;
    }
}
