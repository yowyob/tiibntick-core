package com.yowyob.tiibntick.core.gofreelancer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Bounds the cost of the two GOFP provisioning web filters
 * ({@code GofpUserProvisioningFilter}, {@code GofpFreelancerProvisioningFilter}).
 *
 * <ul>
 *   <li>{@code cache-max-size} / {@code cache-ttl}: once a user's rows are known to
 *       exist, the filters skip the database for that user until the entry expires.
 *       The TTL is the upper bound on how long a row deleted in the database keeps
 *       being treated as present by this instance.</li>
 *   <li>{@code projection-timeout}: the longest a request may wait for the freelancer
 *       projection before it proceeds anyway (the projection is retried on the next
 *       matching request).</li>
 * </ul>
 *
 * @author KOUAM Kamdem
 */
@Getter
@Setter
@ConfigurationProperties("tnt.gofp.provisioning")
public class GofpProvisioningProperties {

    private long cacheMaxSize = 10_000;

    private Duration cacheTtl = Duration.ofMinutes(10);

    private Duration projectionTimeout = Duration.ofSeconds(2);
}
