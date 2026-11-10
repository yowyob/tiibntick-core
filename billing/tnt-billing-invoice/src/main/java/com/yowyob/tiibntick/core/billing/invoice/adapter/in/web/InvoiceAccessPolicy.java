package com.yowyob.tiibntick.core.billing.invoice.adapter.in.web;

import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.billing.invoice.domain.exception.InvoiceNotFoundException;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.Invoice;
import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;

/**
 * Who may see <em>which</em> invoice.
 *
 * <p>{@code @RequirePermission(invoice:read)} only says "may read invoices"; every CLIENT holds it, so
 * without a row-level rule any client could read any other client's invoices by number, mission or
 * client id. The rule: staff (holders of {@code invoice:issue} — agency managers, admins) see every
 * invoice of their tenant; anybody else sees only the invoices where they are the <b>client</b> or the
 * <b>issuer</b>, matched on the caller's user id or actor id (the two ids an invoice may have been
 * addressed with). An invoice the caller may not see is reported as not found, not as forbidden, so
 * its existence is not disclosed.
 */
@Component
public class InvoiceAccessPolicy {

    private final TntPermissionEvaluator permissionEvaluator;

    public InvoiceAccessPolicy(TntPermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    /** Staff = may issue invoices (agency managers, platform admins). */
    public Mono<Boolean> isStaff() {
        return permissionEvaluator.canFromCurrentContext("invoice", "issue");
    }

    /** Completes when the caller is staff, errors with 403 otherwise. */
    public Mono<Void> requireStaff() {
        return isStaff().flatMap(staff -> staff
                ? Mono.<Void>empty()
                : Mono.error(TntRoleException.forbidden("invoice", "issue")));
    }

    /** The ids an invoice may carry for this caller as client or issuer. */
    public static Set<String> idsOf(TntUserIdentity user) {
        Set<String> ids = new HashSet<>();
        if (user.userId() != null) ids.add(user.userId().toString());
        if (user.actorId() != null) ids.add(user.actorId().toString());
        return ids;
    }

    public Mono<Invoice> readable(Invoice invoice, TntUserIdentity user) {
        return isStaff().flatMap(staff -> staff || isParty(invoice, user)
                ? Mono.just(invoice)
                : Mono.error(new InvoiceNotFoundException(invoice.getId())));
    }

    public Flux<Invoice> readable(Flux<Invoice> invoices, TntUserIdentity user) {
        return isStaff().flatMapMany(staff -> staff ? invoices : invoices.filter(i -> isParty(i, user)));
    }

    public static boolean isParty(Invoice invoice, TntUserIdentity user) {
        Set<String> ids = idsOf(user);
        return (invoice.getClientId() != null && ids.contains(invoice.getClientId()))
                || (invoice.getIssuerOrgId() != null && ids.contains(invoice.getIssuerOrgId()));
    }
}
