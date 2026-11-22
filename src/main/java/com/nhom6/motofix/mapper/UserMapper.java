package com.nhom6.motofix.mapper;

import com.nhom6.motofix.dto.request.RegisterRequest;
import com.nhom6.motofix.dto.respond.LoginResponse;
import com.nhom6.motofix.dto.respond.RegisterResponse;
import com.nhom6.motofix.entity.Role;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.Gender;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "vehicles", ignore = true)
    @Mapping(target = "gender", ignore = true)
    @Mapping(target = "currentRole", ignore = true)
    @Mapping(target = "tokenVersion", ignore = true)
    @Mapping(target = "passwordResetToken", ignore = true)
    @Mapping(target = "passwordResetTokenExpiry", ignore = true)
    @Mapping(target = "providerProfile", ignore = true)
    @Mapping(target = "bookings", ignore = true)
    @Mapping(target = "avatarUrl", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(RegisterRequest request);

    @Mapping(target = "roles", source = "roles", qualifiedByName = "mapRolesToStrings")
    RegisterResponse toResponse(User user);

    @Named("mapRolesToStrings")
    default Set<String> mapRolesToStrings(Set<Role> roles) {
        if (roles == null) return Set.of();
        return roles.stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }

    @Mapping(target = "roles", source = "roles", qualifiedByName = "mapRolesToNames")
    @Mapping(target = "currentRole", source = "currentRole")
    @Mapping(target = "gender", source = "gender", qualifiedByName = "mapGenderToString")
    LoginResponse.UserInfo toLoginUserInfo(User user);

    @Named("mapRolesToNames")
    default Set<String> mapRolesToNames(Set<Role> roles) {
        if (roles == null) {
            return Set.of();
        }
        return roles.stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }

    @Named("mapGenderToString")
    default String mapGenderToString(Gender gender) {
        return gender != null ? gender.name() : null;
    }
}
