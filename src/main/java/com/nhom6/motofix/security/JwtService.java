package com.nhom6.motofix.security;

import com.nhom6.motofix.entity.Role;
import com.nhom6.motofix.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {
    private final String secret;
    private final long expiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration
    ) {
        this.secret = secret;
        this.expiration = expiration;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Tạo JWT Token từ thông tin User (Lưu User ID vào Subject, Roles, currentRole, tokenVersion vào Claims)
     */
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();

        claims.put(
                "roles",
                user.getRoles()
                        .stream()
                        .map(Role::getName)
                        .toList()
        );
        claims.put("currentRole", user.getCurrentRole() != null ? user.getCurrentRole() : "CUSTOMER");
        claims.put("tokenVersion", user.getTokenVersion() != null ? user.getTokenVersion() : 0);

        return Jwts.builder()
                .claims(claims)
                .subject(user.getId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUserId(Jwt jwt) {
        return jwt.getSubject();
    }

    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return extractClaim(token, claims -> claims.get("roles", List.class));
    }

    public String extractCurrentRole(String token) {
        return extractClaim(token, claims -> claims.get("currentRole", String.class));
    }

    public Integer extractTokenVersion(String token) {
        return extractClaim(token, claims -> claims.get("tokenVersion", Integer.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public boolean isTokenValid(String token, User user) {
        String userId = extractUserId(token);
        boolean valid = userId != null && userId.equals(user.getId().toString()) && !isTokenExpired(token);
        if (!valid) {
            return false;
        }

        Integer tokenVersionInClaim = extractTokenVersion(token);
        if (tokenVersionInClaim != null && user.getTokenVersion() != null) {
            if (!tokenVersionInClaim.equals(user.getTokenVersion())) {
                return false;
            }
        } else if (tokenVersionInClaim == null && user.getTokenVersion() != null && user.getTokenVersion() > 0) {
            return false;
        }
        return true;
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
