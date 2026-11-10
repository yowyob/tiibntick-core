package com.yowyob.tiibntick.core.billing.report.adapter.in.web;

import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;

/**
 * Which reports a caller may run.
 *
 * <p>Tenant-wide reports (revenue, commissions, margins, KPI, templates) need {@code report:read}, held by
 * management roles. A driver holds only {@code report:own} and may run the per-issuer reports for <b>their own
 * id</b>: the issuer id of the invoices a driver issues is their user id (or actor id), so a driver asking for
 * any other id is refused, not served an empty report.
 */
@Component
public class ReportAccessPolicy {

    private final TntPermissionEvaluator evaluator;

    public ReportAccessPolicy(TntPermissionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /** Management: may read tenant-wide reports and any issuer's report inside the tenant. */
    public Mono<Boolean> isStaff() {
        return evaluator.canFromCurrentContext("report", "read");
    }

    public static Set<String> idsOf(TntUserIdentity user) {
        Set<String> ids = new HashSet<>();
        if (user.userId() != null) ids.add(user.userId().toString());
        if (user.actorId() != null) ids.add(user.actorId().toString());
        return ids;
    }

    public static boolean isOwn(TntUserIdentity user, String issuerId) {
        return issuerId != null && idsOf(user).contains(issuerId);
    }
}
