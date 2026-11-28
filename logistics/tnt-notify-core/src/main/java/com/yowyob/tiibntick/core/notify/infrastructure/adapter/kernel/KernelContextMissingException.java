package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

/**
 * The Kernel notification engine needs a tenant and an organization on every call, and neither the caller nor
 * the platform configuration ({@code tnt.notify.kernel.default-*}) provided one. This is a deployment
 * configuration gap, not a business-state conflict: it is reported as {@code 503}, never as {@code 409}.
 *
 * <p>Still an {@link IllegalStateException} so existing callers that already catch that type keep working.
 */
public class KernelContextMissingException extends IllegalStateException {

    public KernelContextMissingException(String message) {
        super(message);
    }
}
