package com.yowyob.tiibntick.bootstrap.health;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Value object capturing the connectivity status of each database level
 * in the TiiBnTick database pyramid.
 * <p>
 * The pyramid has 3 levels:
 * <ol>
 *   <li>Kernel DB — used by RT-comops (comops-auth-core, comops-actor-core, etc.)</li>
 *   <li>TNT Core DB — used by all tnt-****-core modules (shared PostgreSQL schemas)</li>
 *   <li>Platform DBs — one per TiiBnTick platform (Agency, Go, Link, Point, Freelancer, Market)</li>
 * </ol>
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder(toBuilder = true)
public final class DatabasePyramidStatus {

    private final boolean kernelDbConnected;
    private final boolean tntCoreDbConnected;
    private final boolean agencyDbConnected;
    private final boolean goDbConnected;
    private final boolean linkDbConnected;
    private final boolean pointDbConnected;
    private final boolean freelancerDbConnected;
    private final boolean marketDbConnected;
    private final LocalDateTime checkedAt;

    public boolean allConnected() {
        return kernelDbConnected && tntCoreDbConnected
                && agencyDbConnected && goDbConnected
                && linkDbConnected && pointDbConnected
                && freelancerDbConnected && marketDbConnected;
    }

    public List<String> connectedDatabases() {
        List<String> connected = new ArrayList<>();
        if (kernelDbConnected) connected.add("kernel");
        if (tntCoreDbConnected) connected.add("tnt-core");
        if (agencyDbConnected) connected.add("agency");
        if (goDbConnected) connected.add("go");
        if (linkDbConnected) connected.add("link");
        if (pointDbConnected) connected.add("point");
        if (freelancerDbConnected) connected.add("freelancer");
        if (marketDbConnected) connected.add("market");
        return connected;
    }

    public List<String> failedDatabases() {
        List<String> failed = new ArrayList<>();
        if (!kernelDbConnected) failed.add("kernel");
        if (!tntCoreDbConnected) failed.add("tnt-core");
        if (!agencyDbConnected) failed.add("agency");
        if (!goDbConnected) failed.add("go");
        if (!linkDbConnected) failed.add("link");
        if (!pointDbConnected) failed.add("point");
        if (!freelancerDbConnected) failed.add("freelancer");
        if (!marketDbConnected) failed.add("market");
        return failed;
    }

    /**
     * Creates a "local check" status — for the monolith dev setup where all
     * modules share the same PostgreSQL instance, tnt-core is always the primary.
     * Platform DBs are connected when the single instance is up.
     */
    public static DatabasePyramidStatus checkLocal() {
        return DatabasePyramidStatus.builder()
                .kernelDbConnected(true)
                .tntCoreDbConnected(true)
                .agencyDbConnected(true)
                .goDbConnected(true)
                .linkDbConnected(true)
                .pointDbConnected(true)
                .freelancerDbConnected(true)
                .marketDbConnected(true)
                .checkedAt(LocalDateTime.now())
                .build();
    }

    public static DatabasePyramidStatus unknown() {
        return DatabasePyramidStatus.builder()
                .checkedAt(LocalDateTime.now())
                .build();
    }
}
