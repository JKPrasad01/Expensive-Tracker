package com.expensetracker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    @Getter
    private final long expirationMs;
    private final String issuer;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:1800000}") long expirationMs,
            @Value("${jwt.issuer:expense-tracker}") String issuer) {

        byte[] keyBytes = secret == null
                ? new byte[0]
                : secret.getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret must be at least 32 bytes long");
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
        this.issuer = issuer;
    }

    public String generateToken(String email, Long userId) {

        Map<String, Object> claims = new HashMap<>();
        claims.put(SecurityConstants.CLAIM_USER_ID, userId);
        claims.put(SecurityConstants.CLAIM_JTI, UUID.randomUUID().toString());

        return Jwts.builder()
                .claims(claims)
                .subject(email)
                .issuer(issuer)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseClaims(String token) {

        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(Claims claims) {
        return claims.getSubject();
    }

    public Long extractUserId(Claims claims) {
        return claims.get(SecurityConstants.CLAIM_USER_ID, Long.class);
    }

    public String extractJti(Claims claims) {
        return claims.getId();
    }

    public boolean validateToken(Claims claims, UserDetails userDetails) {

        return claims.getSubject().equals(userDetails.getUsername())
                && claims.getExpiration().after(new Date());
    }

}
