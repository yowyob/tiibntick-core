package com.yowyob.tiibntick.core.billing.report.adapter.in.web.mapper;

import com.yowyob.tiibntick.core.billing.report.adapter.in.web.dto.response.*;
import com.yowyob.tiibntick.core.billing.report.domain.model.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for billing report REST layer.
 *
 * @author MANFOUO Braun
 */
@Mapper(componentModel = "spring")
public interface ReportWebMapper {

    @Mapping(target = "collectionRatePercent", expression = "java(report.collectionRatePercent())")
    RevenueReportResponse toResponse(RevenueReport report);

    CommissionSummaryResponse toResponse(CommissionSummary summary);

    MarginReportResponse toResponse(MarginReport report);

    BillingKPISnapshotResponse toResponse(BillingKPISnapshot snapshot);
}
