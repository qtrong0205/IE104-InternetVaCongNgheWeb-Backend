package com.nhom6.motofix.security;

import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter kiểm tra và xác thực JWT Token gửi kèm trong Header Authorization của Request HTTP
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    /**
     * Hàm trích xuất Token từ Header, xác thực với CSDL và nạp thông tin User vào SecurityContext
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Bỏ qua nếu Header không có Token hoặc không đúng định dạng "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        try {
            final String userId = jwtService.extractUserId(token);

            // Chỉ xử lý nếu lấy được userId và người dùng chưa được xác thực trong phiên này
            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                User user = userRepository.findById(UUID.fromString(userId)).orElse(null);

                // Kiểm tra User có tồn tại và Token có hợp lệ (đúng chữ ký & chưa hết hạn)
                if (user != null && jwtService.isTokenValid(token, user)) {

                    // Thêm tiền tố "ROLE_" vào tên Role để khớp với chuẩn phân quyền của Spring Security (ví dụ: USER -> ROLE_USER)
                    var authorities = user.getRoles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                            .toList();

                    // Khởi tạo đối tượng xác thực cho Spring Security
                    var authentication = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            authorities
                    );

                    // Ghi nhận thêm thông tin từ Request (như địa chỉ IP)
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    // Nạp User đã xác thực vào SecurityContextHolder
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception ignored) {
            // Token bị lỗi/giả mạo/hết hạn: Bỏ qua exception để Spring Security tự chặn ở các bước sau
        }

        filterChain.doFilter(request, response);
    }
}
