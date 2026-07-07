package com.yowyob.tiibntick.bootstrap.deployment;

/**
 * Canonical codes for the 6 TiiBnTick sub-platforms.
 * Used by {@link TntDataSourceConfig} to identify platform-specific
 * database connection factories and by {@link DockerImageDescriptor}
 * for environment variable descriptors.
 *
 * @author MANFOUO Braun
 */
public enum PlatformCode {
    AGENCY,
    GO,
    LINK,
    POINT,
    FREELANCER,
    MARKET
}
