package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.mapper;

import com.yowyob.tiibntick.core.dispute.domain.enums.ResolutionType;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.dto.response.DisputeResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.yowyob.tiibntick.core.dispute.support.DisputeFixtures.inMediation;
import static com.yowyob.tiibntick.core.dispute.support.DisputeFixtures.opened;
import static com.yowyob.tiibntick.core.dispute.support.DisputeFixtures.rule;
import static org.assertj.core.api.Assertions.assertThat;

/** Lot L4: the detail response carries, at its end, the responsible party and the closing time. */
class DisputeRestMapperL4Test {

    @Test
    @DisplayName("responsibleParty and closedAt after a ruling; null while open")
    void lifecycleFields() {
        Dispute ruled = inMediation();
        ruled.rule(rule(ruled, ResolutionType.COMPLAINT_DISMISSED, null));
        DisputeResponses.DisputeDetailResponse r = DisputeRestMapper.toDetailResponse(ruled);
        assertThat(r.status()).isEqualTo("CLOSED_RESOLVED");
        assertThat(r.responsibleParty()).isEqualTo("COURIER");
        assertThat(r.closedAt()).isNotNull();

        DisputeResponses.DisputeDetailResponse open = DisputeRestMapper.toDetailResponse(opened());
        assertThat(open.responsibleParty()).isNull();
        assertThat(open.closedAt()).isNull();
    }
}
