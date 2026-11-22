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
import java.util.ArrayList;
import java.util.List;
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

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        try {
            final String userId = jwtService.extractUserId(token);

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                User user = userRepository.findById(UUID.fromString(userId)).orElse(null);

                if (user != null && jwtService.isTokenValid(token, user)) {

                    String currentRole = user.getCurrentRole();
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();

                    if ("CUSTOMER".equalsIgnoreCase(currentRole)) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
                        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                    } else if ("PROVIDER".equalsIgnoreCase(currentRole)) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_PROVIDER"));
                    } else {
                        // Null hoặc chưa thiết lập: cấp theo roles gốc để tương thích ngược fixture cũ
                        for (var role : user.getRoles()) {
                            String rName = role.getName();
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + rName));
                            if ("USER".equalsIgnoreCase(rName)) {
                                authorities.add(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
                            } else if ("CUSTOMER".equalsIgnoreCase(rName)) {
                                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                            }
                        }
                    }

                    // ADMIN luôn được giữ nếu user có role ADMIN
                    for (var role : user.getRoles()) {
                        if ("ADMIN".equalsIgnoreCase(role.getName())) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                        }
                    }

                    var authentication = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            authorities
                    );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception ignored) {
        }

        filterChain.doFilter(request, response);
    }
}
