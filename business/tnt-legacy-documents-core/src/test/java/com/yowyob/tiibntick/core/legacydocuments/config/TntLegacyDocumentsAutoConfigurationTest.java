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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context-load smoke test for {@link TntLegacyDocumentsAutoConfiguration} — same
 * validation technique used for {@code tnt-hrm-core} (see the workstream doc, step 8):
 * proves the module boots standalone (only a stand-in {@code kernelWebClient} bean
 * supplied, exactly as {@code tnt-bootstrap}'s real {@code KernelBridgeConfig} would
 * provide it) and registers all 7 document-type proxy controllers plus the Kernel
 * gateway adapter/service, without needing the full application context.
 *
 * @author MANFOUO Braun
 */
class TntLegacyDocumentsAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
            .withBean("kernelWebClient", WebClient.class, WebClient::create)
            .withConfiguration(AutoConfigurations.of(TntLegacyDocumentsAutoConfiguration.class));

    @Test
    @DisplayName("context loads and registers all 7 commercial-document proxy controllers")
    void contextLoadsWithAllDocumentControllers() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(BonCommandeController.class);
            assertThat(context).hasSingleBean(BonAchatController.class);
            assertThat(context).hasSingleBean(BonLivraisonController.class);
            assertThat(context).hasSingleBean(FactureFournisseurController.class);
            assertThat(context).hasSingleBean(FactureProformaController.class);
            assertThat(context).hasSingleBean(BonReceptionController.class);
            assertThat(context).hasSingleBean(NoteCreditController.class);
            assertThat(context).hasSingleBean(KernelLegacyDocumentGatewayAdapter.class);
            assertThat(context).hasSingleBean(KernelLegacyDocumentGatewayService.class);
        });
    }
}
