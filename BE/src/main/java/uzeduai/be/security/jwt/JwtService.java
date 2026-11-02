package uzeduai.be.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import uzeduai.be.common.enums.TenantRole;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] bytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.security.jwt.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String createAccessToken(
            UUID userId,
            String email,
            List<String> platformRoles,
            UUID tenantId,
            UUID workspaceId,
            TenantRole tenantRole
    ) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getAccessTokenTtlMinutes() * 60);
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", platformRoles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp));
        if (tenantId != null) {
            builder.claim("tenantId", tenantId.toString());
        }
        if (workspaceId != null) {
            builder.claim("workspaceId", workspaceId.toString());
        }
        if (tenantRole != null) {
            builder.claim("tenantRole", tenantRole.name());
        }
        return builder.signWith(key).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID userId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }
}
