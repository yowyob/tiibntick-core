package com.yowyob.tiibntick.core.geo.domain.model;

import java.time.Instant;
import java.util.*;

/**
 * Aggregate root representing the complete road network for a given tenant.
 * Encapsulates the directed weighted graph (nodes + arcs) and builds
 * an adjacency list for efficient A* traversal in tnt-route-core.
 *
 * This object is designed to be loaded into memory and cached in Redis (TTL 5 min)
 * by tnt-route-core. It is immutable once built.
 *
 * Author: MANFOUO Braun
 */
public final class RoadNetwork {

    private final UUID tenantId;
    private final Map<RoadNodeId, RoadNode> nodes;
    private final Map<RoadArcId, RoadArc> arcs;
    private final Map<RoadNodeId, List<RoadArc>> adjacencyList;
    private final Instant builtAt;

    private RoadNetwork(UUID tenantId, Map<RoadNodeId, RoadNode> nodes,
                        Map<RoadArcId, RoadArc> arcs) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.nodes = Collections.unmodifiableMap(new HashMap<>(nodes));
        this.arcs = Collections.unmodifiableMap(new HashMap<>(arcs));
        this.adjacencyList = buildAdjacencyList(arcs);
        this.builtAt = Instant.now();
    }

    public static RoadNetwork build(UUID tenantId, Collection<RoadNode> nodeList,
                                    Collection<RoadArc> arcList) {
        Map<RoadNodeId, RoadNode> nodesMap = new HashMap<>();
        for (RoadNode node : nodeList) {
            nodesMap.put(node.id(), node);
        }
        Map<RoadArcId, RoadArc> arcsMap = new HashMap<>();
        for (RoadArc arc : arcList) {
            arcsMap.put(arc.id(), arc);
        }
        return new RoadNetwork(tenantId, nodesMap, arcsMap);
    }

    private static Map<RoadNodeId, List<RoadArc>> buildAdjacencyList(Map<RoadArcId, RoadArc> arcs) {
        Map<RoadNodeId, List<RoadArc>> adj = new HashMap<>();
        for (RoadArc arc : arcs.values()) {
            adj.computeIfAbsent(arc.sourceId(), k -> new ArrayList<>()).add(arc);
            if (arc.isBidirectional()) {
                RoadArc reversed = RoadArc.rehydrate(
                        arc.id(), arc.tenantId(), arc.targetId(), arc.sourceId(),
                        arc.distanceKm(), arc.roadType(), arc.baseSpeedKmh(),
                        arc.trafficFactor(), false, arc.createdAt(), arc.updatedAt()
                );
                adj.computeIfAbsent(arc.targetId(), k -> new ArrayList<>()).add(reversed);
            }
        }
        return Collections.unmodifiableMap(adj);
    }

    public Optional<RoadNode> findNode(RoadNodeId id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public List<RoadArc> outgoingArcs(RoadNodeId nodeId) {
        return adjacencyList.getOrDefault(nodeId, Collections.emptyList());
    }

    public boolean containsNode(RoadNodeId id) {
        return nodes.containsKey(id);
    }

    public int nodeCount() { return nodes.size(); }
    public int arcCount()  { return arcs.size(); }

    public UUID tenantId()                                 { return tenantId; }
    public Map<RoadNodeId, RoadNode> nodes()               { return nodes; }
    public Map<RoadArcId, RoadArc> arcs()                  { return arcs; }
    public Map<RoadNodeId, List<RoadArc>> adjacencyList()  { return adjacencyList; }
    public Instant builtAt()                               { return builtAt; }

    @Override
    public String toString() {
        return "RoadNetwork{tenantId=" + tenantId + ", nodes=" + nodeCount()
                + ", arcs=" + arcCount() + ", builtAt=" + builtAt + "}";
    }
}
