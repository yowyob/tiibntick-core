package com.yowyob.tiibntick.core.linkback.adapter.out.realtime;

import com.yowyob.tiibntick.common.util.TntGeohashUtil;
import com.yowyob.tiibntick.core.linkback.adapter.out.realtime.dto.AlertTileEvent;
import com.yowyob.tiibntick.core.linkback.adapter.out.realtime.dto.NodeTileEvent;
import com.yowyob.tiibntick.core.linkback.application.port.out.ILinkTileEventPublisher;
import com.yowyob.tiibntick.core.linkback.domain.model.NetworkAlert;
import com.yowyob.tiibntick.core.linkback.domain.model.NetworkNode;
import com.yowyob.tiibntick.core.realtime.application.port.out.IWebSocketBroadcaster;
import com.yowyob.tiibntick.core.realtime.domain.model.BroadcastTopic;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Delegates the Link live-map fan-out to {@code tnt-realtime-core}'s STOMP+Redis engine
 * (Chantier G — reuses the existing multi-instance broadcaster, per Audit n7 P1 bis: "ne
 * pas réécrire un second moteur"). Each entity is published on the geohash tile channel
 * covering its current location.
 *
 * @author MANFOUO Braun
 */
@Component
@RequiredArgsConstructor
public class LinkTileRealtimePublisher implements ILinkTileEventPublisher {

    private final IWebSocketBroadcaster broadcaster;

    @Override
    public Mono<Void> publishNodeMoved(NetworkNode node) {
        if (node.getLastKnownLocation() == null) {
            return Mono.empty();
        }
        String geohash = TntGeohashUtil.encode(
                node.getLastKnownLocation().latitude(), node.getLastKnownLocation().longitude());
        NodeTileEvent event = new NodeTileEvent(
                node.getId(), node.getTenantId(), node.getRefType().name(), node.getRefId(),
                node.getStatus().name(),
                node.getLastKnownLocation().latitude(), node.getLastKnownLocation().longitude(),
                node.getHeading(), node.getTrustScore(), node.getGamificationLevel(),
                node.getUpdatedAt());
        return broadcaster.broadcast(BroadcastTopic.forTile(geohash), event);
    }

    @Override
    public Mono<Void> publishAlertReported(NetworkAlert alert) {
        return publishAlertTile(alert);
    }

    @Override
    public Mono<Void> publishAlertResolved(NetworkAlert alert) {
        return publishAlertTile(alert);
    }

    private Mono<Void> publishAlertTile(NetworkAlert alert) {
        String geohash = TntGeohashUtil.encode(alert.getLocation().latitude(), alert.getLocation().longitude());
        AlertTileEvent event = new AlertTileEvent(
                alert.getId(), alert.getTenantId(), alert.getType().name(), alert.getSeverity().name(),
                alert.getStatus().name(), alert.getLocation().latitude(), alert.getLocation().longitude(),
                alert.getUpdatedAt());
        return broadcaster.broadcast(BroadcastTopic.forTile(geohash), event);
    }
}
