package com.nhom6.motofix.security;

import com.nhom6.motofix.entity.Role;
import com.nhom6.motofix.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
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

    // Tạo mã SecretKey từ chuỗi cấu hình Base64 để ký và giải mã Token
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Tạo JWT Token từ thông tin User (Lưu User ID vào Subject và Roles vào Claims)
     */
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();

        // Đưa danh sách quyền (roles) vào payload
        claims.put(
                "roles",
                user.getRoles()
                        .stream()
                        .map(Role::getName)
                        .toList()
        );

        return Jwts.builder()
                .claims(claims)
                .subject(user.getId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Trích xuất User ID (Subject) lưu trong Token
     */
    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Trích xuất danh sách Roles lưu trong Token (Phục vụ cho Security Filter)
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return extractClaim(token, claims -> claims.get("roles", List.class));
    }

    /**
     * Hàm generic hỗ trợ trích xuất một thuộc tính bất kỳ trong Claims
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Kiểm tra Token có hợp lệ với User hay không (Đúng User ID và chưa hết hạn)
     */
    public boolean isTokenValid(String token, User user) {
        String userId = extractUserId(token);
        // Sửa lỗi cú pháp: dùng toán tử && thay vì .and()
        return userId.equals(user.getId().toString()) && !isTokenExpired(token);
    }

    // Kiểm tra Token đã hết hạn hay chưa
    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    // Giải mã Token để lấy toàn bộ thông tin trong Payload (Ném lỗi nếu Token sai chữ ký/hết hạn)
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
