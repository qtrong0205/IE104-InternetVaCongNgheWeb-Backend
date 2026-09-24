package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.RegisterRequest;
import com.nhom6.motofix.dto.respond.RegisterResponse;
import com.nhom6.motofix.entity.Role;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.mapper.UserMapper;
import com.nhom6.motofix.repository.RoleRepository;
import com.nhom6.motofix.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserMapper userMapper;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        // 1. Normalize dữ liệu
        String email = request.email().trim().toLowerCase();
        String phone = request.phone().trim();

        // 2. Kiểm tra email
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }

        // 3. Kiểm tra phone
        if (userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("Phone already exists");
        }

        // 4. Tìm role USER
        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() ->
                        new IllegalStateException("USER role does not exist")
                );

        // 5. Tạo user
        User user = userMapper.toEntity(request);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.getRoles().add(userRole);

        // 6. Lưu user
        User savedUser = userRepository.save(user);

        // 7. Convert roles
        var roles = savedUser.getRoles()
                .stream()
                .map(Role::getName)
                .collect(java.util.stream.Collectors.toSet());

        // 8. Response
        return userMapper.toResponse(savedUser);
    }
}
