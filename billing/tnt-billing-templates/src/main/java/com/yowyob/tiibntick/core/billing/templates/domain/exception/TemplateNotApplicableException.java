package com.yowyob.tiibntick.core.billing.templates.domain.exception;

import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;

/**
 * Thrown when an actor attempts to apply a {@code PolicyTemplate} that is not
 * designated for their actor type.
 *
 * <p>Example: A POINT operator attempting to apply {@code TPL-INTER_CITY},
 * which is only applicable to AGENCY and FREELANCER_ORG.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public class TemplateNotApplicableException extends RuntimeException {

    public TemplateNotApplicableException(String templateCode, PolicyOwnerType ownerType) {
        super("Template '" + templateCode + "' is not applicable to actor type: " + ownerType
                + ". Check the 'applicableTo' list of the template.");
    }
}
