package com.yowyob.tiibntick.core.legacydocuments.config;

import com.yowyob.tiibntick.core.legacydocuments.adapter.in.web.BonAchatController;
import com.yowyob.tiibntick.core.legacydocuments.adapter.in.web.BonCommandeController;
import com.yowyob.tiibntick.core.legacydocuments.adapter.in.web.BonLivraisonController;
import com.yowyob.tiibntick.core.legacydocuments.adapter.in.web.BonReceptionController;
import com.yowyob.tiibntick.core.legacydocuments.adapter.in.web.FactureFournisseurController;
import com.yowyob.tiibntick.core.legacydocuments.adapter.in.web.FactureProformaController;
import com.yowyob.tiibntick.core.legacydocuments.adapter.in.web.NoteCreditController;
import com.yowyob.tiibntick.core.legacydocuments.adapter.out.kernel.KernelLegacyDocumentGatewayAdapter;
import com.yowyob.tiibntick.core.legacydocuments.application.service.KernelLegacyDocumentGatewayService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration for the {@code tnt-legacy-documents-core} module — a pure Kernel
 * {@code billing-legacy-documents-controller} proxy, no persistence of its own. All beans
 * are plain {@code @Component}/{@code @Service}/{@code @RestController} classes, discovered
 * via this module-local {@link ComponentScan} (redundant with {@code tnt-bootstrap}'s
 * blanket {@code com.yowyob.tiibntick} scan, kept for the module to stay independently
 * bootable/testable — same convention as {@code tnt-hrm-core}'s
 * {@code TntHrmAutoConfiguration}, whose codegen technique this module reuses).
 *
 * <p><strong>Coexistence ADR:</strong> this module never depends on, and is never depended
 * on by, {@code tnt-billing-invoice} — see the module {@code pom.xml} description and
 * {@code docs/audits/remediation/workstream-payment-billing-kernel-delegation.md}.
 *
 * @author MANFOUO Braun
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ComponentScan(basePackageClasses = {
        // ── Kernel outbound adapter + gateway service ───────────────────────────
        KernelLegacyDocumentGatewayAdapter.class,
        KernelLegacyDocumentGatewayService.class,
        // ── Web adapters (one controller per commercial-document type) ──────────
        BonCommandeController.class,
        BonAchatController.class,
        BonLivraisonController.class,
        FactureFournisseurController.class,
        FactureProformaController.class,
        BonReceptionController.class,
        NoteCreditController.class
})
public class TntLegacyDocumentsAutoConfiguration {
}
