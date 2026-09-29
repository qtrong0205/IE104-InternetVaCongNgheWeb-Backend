package com.nhom6.motofix.mapper;

import com.nhom6.motofix.dto.request.ProviderProfileRequest;
import com.nhom6.motofix.dto.respond.ProviderProfileResponse;
import com.nhom6.motofix.entity.ProviderProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProviderProfileMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "approvalStatus", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "ratingAvg", ignore = true)
    @Mapping(target = "ratingCount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    ProviderProfile toEntity(ProviderProfileRequest request);

    ProviderProfileResponse toResponse(ProviderProfile providerProfile);
}