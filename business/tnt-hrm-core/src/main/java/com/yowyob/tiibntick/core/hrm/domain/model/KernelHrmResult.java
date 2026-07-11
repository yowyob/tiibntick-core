package com.yowyob.tiibntick.core.hrm.domain.model;

/**
 * Result of proxying one call to a Kernel HRM endpoint — carries the real Kernel
 * HTTP status alongside the envelope so the controller can respond with it
 * (400/404/409/...) instead of flattening every outcome to 200.
 *
 * @author MANFOUO Braun
 */
public record KernelHrmResult(int httpStatus, KernelApiEnvelope envelope) {
}
