package com.yowyob.tiibntick.core.legacydocuments.application.port.out;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.legacydocuments.domain.model.KernelDocumentResult;
import org.springframework.http.HttpMethod;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

/**
 * Secondary (outbound) port: calls the Kernel's {@code billing-legacy-documents-controller}
 * HTTP surface — the 7 commercial-document types (bon-commande, bons-achat, bons-livraison,
 * facture-fournisseurs, factures-proforma, bon-receptions, note-credits) and their shared
 * {@code payments/bank}, {@code payments/cashier}, {@code sync/accounting-invoice},
 * {@code sync/cashier-bill} sub-operations — see {@code docs/kernel-api/endpoints.md:4224-4702}
 * for the full catalogue (64 operations).
 *
 * <p>Implemented by {@code KernelLegacyDocumentGatewayAdapter} using the shared
 * {@code kernelWebClient} bean — never a Kernel Spring bean/type (see root {@code CLAUDE.md}:
 * Kernel is HTTP-only).
 *
 * @author MANFOUO Braun
 */
public interface IKernelLegacyDocumentGatewayPort {

    Mono<KernelDocumentResult> invoke(HttpMethod method, String kernelPath, MultiValueMap<String, String> queryParams,
                                       JsonNode body, String bearerAuthorization);
}
