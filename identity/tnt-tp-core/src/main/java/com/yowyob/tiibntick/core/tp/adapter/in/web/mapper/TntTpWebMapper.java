package com.yowyob.tiibntick.core.tp.adapter.in.web.mapper;

import com.yowyob.tiibntick.core.tp.adapter.in.web.dto.response.*;
import com.yowyob.tiibntick.core.tp.domain.model.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between domain models and REST response DTOs.
 *
 * @author MANFOUO Braun
 */
@Mapper(componentModel = "spring")
public interface TntTpWebMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "tntRoles", source = "tntRoles")
    @Mapping(target = "phoneMasked", source = "phoneMasked")
    ClientProfileResponse toResponse(TntClientProfile profile);

    @Mapping(target = "maxDiscountXaf", expression = "java(account.maxDiscountXaf())")
    LoyaltyAccountResponse toResponse(LoyaltyAccount account);

    KycRecordResponse toResponse(KycRecord kycRecord);

    RatingResponse toResponse(ThirdPartyRating rating);
}
