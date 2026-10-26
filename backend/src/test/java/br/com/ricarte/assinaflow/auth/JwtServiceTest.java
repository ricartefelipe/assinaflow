package br.com.ricarte.assinaflow.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {
    private static final String SECRET = "synthetic-test-key-only-32-bytes-or-more";
    private final JwtService service = new JwtService(SECRET, Duration.ofHours(1));

    @Test
    void acceptsIssuedTokenAndRejectsWrongSignatureExpiredIssuerAndAudience() {
        UUID id = UUID.randomUUID();
        assertThat(service.userId(service.parse(service.createToken(id, "test@example.test", "USER")))).isEqualTo(id);
        assertThatThrownBy(() -> service.parse(new JwtService("different-synthetic-key-32-bytes-or-more", Duration.ofHours(1))
                .createToken(id, "test@example.test", "ADMIN"))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.parse(new JwtService(SECRET, Duration.ofSeconds(-1))
                .createToken(id, "test@example.test", "USER"))).isInstanceOf(JwtException.class);
        for (String[] pair : new String[][]{{"other", "assinaflow-api"}, {"assinaflow", "other"}}) {
            String token = Jwts.builder().subject(id.toString()).issuer(pair[0]).audience().add(pair[1]).and()
                    .expiration(Date.from(Instant.now().plusSeconds(60)))
                    .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();
            assertThatThrownBy(() -> service.parse(token)).isInstanceOf(JwtException.class);
        }
    }
}
