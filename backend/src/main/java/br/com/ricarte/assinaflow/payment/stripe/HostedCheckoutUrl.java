package br.com.ricarte.assinaflow.payment.stripe;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Destino aceito para o redirecionamento ao Checkout hospedado: exatamente a origem
 * {@code https://checkout.stripe.com}, sem porta explicita, sem credenciais na URL e sem fragmento.
 * Qualquer outra forma (subdominio parecido, http, porta, userinfo, URL relativa) e recusada.
 */
public final class HostedCheckoutUrl {

    static final String SCHEME = "https";
    static final String HOST = "checkout.stripe.com";

    private HostedCheckoutUrl() {
    }

    /**
     * @return a URL normalizada quando pertence ao Checkout hospedado
     * @throws IllegalStateException quando o destino nao e o Checkout hospedado
     */
    public static String require(String raw) {
        URI uri = parse(raw);
        boolean hosted = SCHEME.equals(uri.getScheme())
                && HOST.equals(uri.getHost())
                && uri.getPort() == -1
                && uri.getRawUserInfo() == null
                && uri.getRawFragment() == null;
        if (!hosted) {
            throw new IllegalStateException("Invalid hosted URL");
        }
        return uri.toString();
    }

    private static URI parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Invalid hosted URL");
        }
        try {
            return new URI(raw);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid hosted URL", e);
        }
    }
}
