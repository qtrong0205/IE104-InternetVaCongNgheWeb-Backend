package com.nhom6.motofix.mapper;

import com.nhom6.motofix.dto.request.RegisterRequest;
import com.nhom6.motofix.dto.respond.RegisterResponse;
import com.nhom6.motofix.entity.Role;
import com.nhom6.motofix.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {
    // Map từ DTO sang Entity
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    User toEntity(RegisterRequest request);

    // Map từ Entity sang Response DTO
    @Mapping(target = "roles", source = "roles", qualifiedByName = "mapRolesToStrings")
    RegisterResponse toResponse(User user);

    // Custom method chuyển Set<Role> thành Set<String>
    @Named("mapRolesToStrings")
    default Set<String> mapRolesToStrings(Set<Role> roles) {
        if (roles == null) return Set.of();
        return roles.stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }
}
