package com.yowyob.tiibntick.core.administration.onboarding.domain.exception;

/**
 * Domain exception for the agency onboarding orchestration (see
 * {@code CORE_KERNEL_GATEWAY_SPEC.md} Bloc C). Never wraps a raw {@code WebClient}
 * exception — carries the Kernel's own {@code errorCode}/{@code message} when available.
 *
 * @author MANFOUO Braun
 */
public class AgencyOnboardingException extends RuntimeException {

    private final String code;

    public AgencyOnboardingException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static AgencyOnboardingException kernelStepFailed(String step, String kernelErrorCode, String kernelMessage) {
        return new AgencyOnboardingException(
                "ONBOARDING_KERNEL_STEP_FAILED",
                "Kernel step '" + step + "' failed"
                        + (kernelErrorCode != null ? " [" + kernelErrorCode + "]" : "")
                        + (kernelMessage != null ? ": " + kernelMessage : ""));
    }

    public static AgencyOnboardingException ownerRoleNotFound(String roleCode) {
        return new AgencyOnboardingException(
                "ONBOARDING_OWNER_ROLE_NOT_FOUND",
                "No Kernel administration role found with code '" + roleCode + "' after provisioning defaults");
    }
}
