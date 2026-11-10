package com.yowyob.tiibntick.core.billing.invoice.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.billing.invoice.adapter.in.web.mapper.InvoiceWebMapper;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.GenerateInvoiceCommand;
import com.yowyob.tiibntick.core.billing.invoice.application.port.out.CreditNoteRepository;
import com.yowyob.tiibntick.core.billing.invoice.application.port.out.InvoiceEventPublisher;
import com.yowyob.tiibntick.core.billing.invoice.application.port.out.InvoicePdfPort;
import com.yowyob.tiibntick.core.billing.invoice.application.port.out.InvoiceRepository;
import com.yowyob.tiibntick.core.billing.invoice.application.port.out.InvoiceSequencePort;
import com.yowyob.tiibntick.core.billing.invoice.application.service.InvoiceService;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.Invoice;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.InvoiceLine;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.InvoiceStatus;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.LineItemType;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntPermissionAspect;
import com.yowyob.tiibntick.core.roles.adapter.in.web.TntRoleExceptionHandler;
import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Row-level access to invoices, driven through the real controller, the real {@link InvoiceService}
 * behind the real permission aspect, and an in-memory repository — only the transport is faked.
 * Invoices are created through the in-process system path (no security context), which is also what
 * the Kafka consumers and the delivery-payment flow use.
 */
class InvoiceAccessControlTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();

    private static final UUID ALICE = UUID.randomUUID();      // client
    private static final UUID BOB = UUID.randomUUID();        // another client
    private static final UUID DRIVER = UUID.randomUUID();     // issues Alice's invoice
    private static final UUID OTHER_DRIVER = UUID.randomUUID();

    private final Map<UUID, Invoice> store = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();
    private InvoiceService service;
    private InvoiceService proxied;
    private TntPermissionEvaluator evaluator;
    private Invoice aliceInvoice;
    private Invoice bobInvoice;

    @BeforeEach
    void setUp() {
        InvoiceRepository repo = new InMemoryInvoiceRepository(store);
        InvoicePdfPort pdf = mock(InvoicePdfPort.class);
        when(pdf.generateAndStore(any())).thenReturn(Mono.just("invoices/x.pdf"));
        when(pdf.getDownloadUrl(any(), anyInt())).thenReturn(Mono.just("https://files.test/x.pdf"));
        InvoiceEventPublisher events = mock(InvoiceEventPublisher.class);
        when(events.publishAll(any(), any())).thenReturn(Mono.empty());
        InvoiceSequencePort sequence = mock(InvoiceSequencePort.class);
        when(sequence.nextSequence(any(), anyInt())).thenAnswer(i -> Mono.just(seq.incrementAndGet()));
        service = new InvoiceService(repo, mock(CreditNoteRepository.class), events, pdf, sequence);

        evaluator = new TntPermissionEvaluator(mock(ReactivePermissionResolver.class), new TntRoleDefinitionRegistry());
        AspectJProxyFactory factory = new AspectJProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAspect(new TntPermissionAspect(evaluator));
        proxied = factory.getProxy();

        aliceInvoice = service.issuePaidInvoiceAsSystem(cmd("M-ALICE", ALICE, DRIVER, 5000), "pay-1").block();
        bobInvoice = service.issuePaidInvoiceAsSystem(cmd("M-BOB", BOB, OTHER_DRIVER, 7000), "pay-2").block();
    }

    private static GenerateInvoiceCommand cmd(String mission, UUID client, UUID issuer, long amount) {
        InvoiceLine line = InvoiceLine.of(1, "Delivery " + mission, 1.0, Money.xaf(amount), BigDecimal.ZERO,
                LineItemType.DELIVERY_FEE);
        return new GenerateInvoiceCommand(TENANT, "TNT", "CM", mission, null, client.toString(), List.of(line),
                List.of(), "XAF", LocalDateTime.now().plusDays(7), "FREELANCER_ORG", issuer.toString(), "Driver",
                false, List.of(), false, null);
    }

    private WebTestClient as(UUID userId, String... authorities) {
        var auth = new TestingAuthenticationToken("u", "n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        auth.setAuthenticated(true);
        InvoiceController controller = new InvoiceController(proxied, new InvoiceWebMapper(),
                new InvoiceAccessPolicy(evaluator));
        return WebTestClient.bindToController(controller)
                .controllerAdvice(new TntRoleExceptionHandler(), new InvoiceExceptionHandler())
                .argumentResolvers(r -> r.addCustomResolver(new IdentityResolver(userId)))
                .webFilter((ex, chain) -> chain.filter(ex)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .build();
    }

    // ── system path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("the in-process system path issues a PAID invoice without any security context")
    void systemPathWorksWithoutSecurityContext() {
        assertThat(aliceInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(aliceInvoice.getIssuerOrgId()).isEqualTo(DRIVER.toString());
        assertThat(aliceInvoice.getClientId()).isEqualTo(ALICE.toString());
    }

    @Test
    @DisplayName("issuing twice for the same mission returns the existing invoice")
    void systemIssueIsIdempotentPerMission() {
        Invoice again = service.issuePaidInvoiceAsSystem(cmd("M-ALICE", ALICE, DRIVER, 5000), "pay-1").block();

        assertThat(again.getId()).isEqualTo(aliceInvoice.getId());
        assertThat(store).hasSize(2);
    }

    @Test
    @DisplayName("the HTTP generate route is still refused without invoice:issue")
    void httpGenerateStillGuarded() {
        // a CLIENT cannot self-issue an invoice
        as(ALICE, "invoice:read", "payment:process").post().uri("/api/v1/billing/invoices/generate")
                .bodyValue("""
                        {"tenantCode":"TNT","countryCode":"CM","missionId":"M-X","clientId":"%s","currency":"XAF",
                         "lines":[{"lineNumber":1,"description":"x","quantity":1}]}
                        """.formatted(ALICE))
                .header("Content-Type", "application/json")
                .exchange().expectStatus().is4xxClientError();
        assertThat(store).hasSize(2);
    }

    // ── a client sees only their own invoices ────────────────────────────────

    @Test
    @DisplayName("a client reads their invoice by id, number and mission")
    void clientReadsOwn() {
        WebTestClient alice = as(ALICE, "invoice:read");

        alice.get().uri("/api/v1/billing/invoices/{id}", aliceInvoice.getId()).exchange().expectStatus().isOk();
        alice.get().uri("/api/v1/billing/invoices/number/{n}", aliceInvoice.getNumber().value())
                .exchange().expectStatus().isOk();
        alice.get().uri("/api/v1/billing/invoices/mission/M-ALICE").exchange().expectStatus().isOk()
                .expectBody(String.class).value(raw -> withJson(raw, b -> assertThat(b.size()).isEqualTo(1)));
    }

    @Test
    @DisplayName("another client's invoice is 404 by id, number and mission — not 403, not leaked")
    void clientCannotReadSomeoneElses() {
        WebTestClient alice = as(ALICE, "invoice:read");

        alice.get().uri("/api/v1/billing/invoices/{id}", bobInvoice.getId()).exchange().expectStatus().isNotFound();
        alice.get().uri("/api/v1/billing/invoices/number/{n}", bobInvoice.getNumber().value())
                .exchange().expectStatus().isNotFound();
        alice.get().uri("/api/v1/billing/invoices/mission/M-BOB").exchange().expectStatus().isOk()
                .expectBody(String.class).value(raw -> withJson(raw, b -> assertThat(b.size()).isZero()));
        alice.get().uri("/api/v1/billing/invoices/client/{c}", BOB).exchange().expectStatus().isOk()
                .expectBody(String.class).value(raw -> withJson(raw, b -> assertThat(b.size()).isZero()));
        alice.get().uri("/api/v1/billing/invoices/{id}/pdf", bobInvoice.getId()).exchange().expectStatus().isNotFound();
    }

    @Test
    @DisplayName("a tenant cannot read another tenant's invoice by number or mission")
    void crossTenantIsNotFound() {
        // staff of OTHER_TENANT hold every invoice permission, but the invoice is not theirs
        WebTestClient stranger = asInTenant(OTHER_TENANT, UUID.randomUUID(), "invoice:read", "invoice:issue");

        stranger.get().uri("/api/v1/billing/invoices/number/{n}", aliceInvoice.getNumber().value())
                .exchange().expectStatus().isNotFound();
        stranger.get().uri("/api/v1/billing/invoices/mission/M-ALICE").exchange().expectStatus().isOk()
                .expectBody(String.class).value(raw -> withJson(raw, b -> assertThat(b.size()).isZero()));
    }

    @Test
    @DisplayName("staff (invoice:issue) see every invoice of their tenant")
    void staffSeeAll() {
        WebTestClient staff = as(UUID.randomUUID(), "invoice:read", "invoice:issue");

        staff.get().uri("/api/v1/billing/invoices/{id}", bobInvoice.getId()).exchange().expectStatus().isOk();
        staff.get().uri("/api/v1/billing/invoices/client/{c}", BOB).exchange().expectStatus().isOk()
                .expectBody(String.class).value(raw -> withJson(raw, b -> assertThat(b.size()).isEqualTo(1)));
    }

    @Test
    @DisplayName("/mine lists the caller's invoices as client, /issued/mine as issuer")
    void mineEndpoints() {
        as(ALICE, "invoice:read").get().uri("/api/v1/billing/invoices/mine").exchange().expectStatus().isOk()
                .expectBody(String.class).value(raw -> withJson(raw, b -> {
                    assertThat(b.size()).isEqualTo(1);
                    assertThat(b.get(0).get("missionId").asText()).isEqualTo("M-ALICE");
                }));
        as(DRIVER, "invoice:read").get().uri("/api/v1/billing/invoices/issued/mine").exchange().expectStatus().isOk()
                .expectBody(String.class).value(raw -> withJson(raw, b -> {
                    assertThat(b.size()).isEqualTo(1);
                    assertThat(b.get(0).get("issuerOrgId").asText()).isEqualTo(DRIVER.toString());
                }));
        as(DRIVER, "invoice:read").get().uri("/api/v1/billing/invoices/mine").exchange().expectStatus().isOk()
                .expectBody(String.class).value(raw -> withJson(raw, b -> assertThat(b.size()).isZero()));
    }

    @Test
    @DisplayName("the issuing driver can read and download their invoice")
    void issuerReadsAndDownloads() {
        WebTestClient driver = as(DRIVER, "invoice:read");

        driver.get().uri("/api/v1/billing/invoices/{id}", aliceInvoice.getId()).exchange().expectStatus().isOk();
        driver.get().uri("/api/v1/billing/invoices/{id}/pdf", aliceInvoice.getId()).exchange().expectStatus().isOk();
        as(OTHER_DRIVER, "invoice:read").get().uri("/api/v1/billing/invoices/{id}", aliceInvoice.getId())
                .exchange().expectStatus().isNotFound();
    }

    // ── mark-paid is not a client action ─────────────────────────────────────

    @Test
    @DisplayName("a client holding payment:process cannot mark an invoice paid")
    void clientCannotMarkPaid() {
        // an ISSUED invoice of Alice
        Invoice issued = service.generateAsSystem(cmd("M-OPEN", ALICE, DRIVER, 1000)).block();

        as(ALICE, "invoice:read", "payment:process").post()
                .uri(b -> b.path("/api/v1/billing/invoices/{id}/mark-paid").queryParam("paymentRef", "fake").build(issued.getId()))
                .exchange().expectStatus().isForbidden();

        assertThat(store.get(issued.getId()).getStatus()).isEqualTo(InvoiceStatus.ISSUED);
    }

    @Test
    @DisplayName("staff can mark an invoice paid")
    void staffCanMarkPaid() {
        Invoice issued = service.generateAsSystem(cmd("M-OPEN2", ALICE, DRIVER, 1000)).block();

        as(UUID.randomUUID(), "invoice:read", "invoice:issue", "payment:process").post()
                .uri(b -> b.path("/api/v1/billing/invoices/{id}/mark-paid").queryParam("paymentRef", "ok").build(issued.getId()))
                .exchange().expectStatus().isOk();

        assertThat(store.get(issued.getId()).getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    // The test WebClient's default codec is Jackson 3, which cannot decode a Jackson 2 JsonNode.
    private static void withJson(String raw, java.util.function.Consumer<JsonNode> assertion) {
        try {
            assertion.accept(new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private WebTestClient asInTenant(UUID tenant, UUID userId, String... authorities) {
        var auth = new TestingAuthenticationToken("u", "n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        auth.setAuthenticated(true);
        InvoiceController controller = new InvoiceController(proxied, new InvoiceWebMapper(),
                new InvoiceAccessPolicy(evaluator));
        return WebTestClient.bindToController(controller)
                .controllerAdvice(new TntRoleExceptionHandler(), new InvoiceExceptionHandler())
                .argumentResolvers(r -> r.addCustomResolver(new IdentityResolver(userId, tenant)))
                .webFilter((ex, chain) -> chain.filter(ex)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .build();
    }

    private record IdentityResolver(UUID userId, UUID tenant) implements HandlerMethodArgumentResolver {
        IdentityResolver(UUID userId) { this(userId, TENANT); }

        @Override public boolean supportsParameter(MethodParameter p) {
            return p.hasParameterAnnotation(CurrentUser.class);
        }

        @Override public Mono<Object> resolveArgument(MethodParameter p, BindingContext c, ServerWebExchange e) {
            return Mono.just(new TntUserIdentity(userId, tenant, null, null, null, Set.of(), false));
        }
    }

    /** Minimal in-memory implementation: only the lookups the controller uses behave realistically. */
    private record InMemoryInvoiceRepository(Map<UUID, Invoice> store) implements InvoiceRepository {
        @Override public Mono<Invoice> save(Invoice i) { store.put(i.getId(), i); return Mono.just(i); }
        @Override public Mono<Invoice> findById(UUID id) { return Mono.justOrEmpty(store.get(id)); }
        @Override public Mono<Invoice> findByIdAndTenantId(UUID id, UUID t) {
            return Mono.justOrEmpty(store.get(id)).filter(i -> t.equals(i.getTenantId()));
        }
        @Override public Mono<Invoice> findByNumber(String n) {
            return Flux.fromIterable(store.values()).filter(i -> i.getNumber().value().equals(n)).next();
        }
        @Override public Flux<Invoice> findByMissionId(String m) {
            return Flux.fromIterable(store.values()).filter(i -> m.equals(i.getMissionId()));
        }
        @Override public Flux<Invoice> findByClientId(UUID t, String c) {
            return Flux.fromIterable(store.values()).filter(i -> t.equals(i.getTenantId()) && c.equals(i.getClientId()));
        }
        @Override public Flux<Invoice> findByTenantId(UUID t) {
            return Flux.fromIterable(store.values()).filter(i -> t.equals(i.getTenantId()));
        }
        @Override public Flux<Invoice> findByIssuerOrgId(UUID t, String o) {
            return Flux.fromIterable(store.values()).filter(i -> t.equals(i.getTenantId()) && o.equals(i.getIssuerOrgId()));
        }
        @Override public Flux<Invoice> findOverdue(LocalDateTime now) { return Flux.empty(); }
        @Override public Mono<Boolean> existsByMissionId(String m) {
            return findByMissionId(m).hasElements();
        }
    }
}
