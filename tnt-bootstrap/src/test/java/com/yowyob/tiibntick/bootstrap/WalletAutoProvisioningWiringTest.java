package com.yowyob.tiibntick.bootstrap;

import com.yowyob.tiibntick.common.session.AuthenticatedSessionListener;
import com.yowyob.tiibntick.common.session.AuthenticatedSessionPublisher;
import com.yowyob.tiibntick.core.actor.adapter.in.web.ClientController;
import com.yowyob.tiibntick.core.actor.adapter.in.web.FreelancerController;
import com.yowyob.tiibntick.core.billing.wallet.application.service.WalletAutoProvisioningListener;
import com.yowyob.tiibntick.core.platformgateway.application.service.KernelAuthGatewayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real application context: the wallet auto-provisioning is actually wired — the wallet
 * listener is a session listener, the publisher sees it, and the login proxy and both
 * profile controllers hold the publisher.
 *
 * @author MANFOUO Braun
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "r2dbc"})
@DisplayName("Wallet auto-provisioning — wiring in the assembled application")
class WalletAutoProvisioningWiringTest {

    @Autowired AuthenticatedSessionPublisher publisher;
    @Autowired List<AuthenticatedSessionListener> listeners;
    @Autowired KernelAuthGatewayService authGateway;
    @Autowired ClientController clientController;
    @Autowired FreelancerController freelancerController;

    @Test
    void everythingIsWired() {
        assertThat(listeners).anyMatch(l -> l instanceof WalletAutoProvisioningListener);
        assertThat(ReflectionTestUtils.getField(authGateway, "sessionPublisher")).isSameAs(publisher);
        assertThat(ReflectionTestUtils.getField(clientController, "sessionPublisher")).isSameAs(publisher);
        assertThat(ReflectionTestUtils.getField(freelancerController, "sessionPublisher")).isSameAs(publisher);
    }
}
