package com.yowyob.tiibntick.core.billing.dsl.application.service;

import com.yowyob.tiibntick.core.billing.dsl.domain.exception.DslParseException;
import com.yowyob.tiibntick.core.billing.dsl.domain.exception.UnsupportedDslAccessException;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAccessLevel;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslRule;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.ValidationError;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast.AstNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates DSL rule expressions against syntax, semantic rules,
 * and access-level restrictions.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@link #validateConditionWithAccessLevel} — validates a condition against
 *       the caller's {@link DslAccessLevel}. Rejects FULL-only variables from
 *       SIMPLIFIED users, and throws for NONE access.</li>
 *   <li>{@link #validateRuleWithAccessLevel} — full rule validation including
 *       access level check.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DslValidatorService {

    private final DslCompilerService compilerService;
    private final DslAccessValidator accessValidator;

    // ── Condition validation ──────────────────────────────────────────────────

    /**
     * Validates a raw condition expression (syntax only, no access level check).
     *
     * @param expression the condition DSL text to validate
     * @return list of validation errors; empty if valid
     */
    public List<ValidationError> validateCondition(String expression) {
        List<ValidationError> errors = new ArrayList<>();
        if (expression == null || expression.isBlank()) {
            errors.add(ValidationError.of("Condition expression must not be blank"));
            return errors;
        }
        try {
            compilerService.compileCondition(expression);
        } catch (DslParseException e) {
            errors.add(ValidationError.of(e.getPosition(), e.getMessage(), e.getToken()));
        } catch (Exception e) {
            errors.add(ValidationError.of(-1, e.getMessage(), ""));
        }
        return errors;
    }

    /**
     * Validates a condition expression AND checks that all variables and operators
     * are allowed for the given {@link DslAccessLevel}.
     *
     * @param expression  the condition DSL text to validate
     * @param accessLevel the access level to enforce
     * @return list of validation errors; empty if fully valid
     * @throws UnsupportedDslAccessException if {@code accessLevel == NONE}
     */
    public List<ValidationError> validateConditionWithAccessLevel(String expression,
                                                                   DslAccessLevel accessLevel) {
        // NONE access — reject immediately
        if (accessLevel == DslAccessLevel.NONE) {
            throw new UnsupportedDslAccessException(accessLevel,
                    "DSL condition authoring is not allowed");
        }

        // Syntax check first
        List<ValidationError> errors = new ArrayList<>(validateCondition(expression));
        if (!errors.isEmpty()) return errors;

        // Access level check (only relevant for SIMPLIFIED)
        if (accessLevel == DslAccessLevel.SIMPLIFIED) {
            try {
                AstNode ast = compilerService.compileCondition(expression);
                errors.addAll(accessValidator.validate(ast, accessLevel));
            } catch (Exception e) {
                // Syntax errors already caught above; this is unexpected
                log.warn("Unexpected error during access-level validation: {}", e.getMessage());
            }
        }
        return errors;
    }

    // ── Action validation ─────────────────────────────────────────────────────

    /**
     * Validates a raw action expression.
     *
     * @param actionExpression the action DSL text to validate
     * @return list of validation errors; empty if valid
     */
    public List<ValidationError> validateAction(String actionExpression) {
        List<ValidationError> errors = new ArrayList<>();
        if (actionExpression == null || actionExpression.isBlank()) {
            errors.add(ValidationError.of("Action expression must not be blank"));
            return errors;
        }
        try {
            compilerService.compileActions(actionExpression);
        } catch (DslParseException e) {
            errors.add(ValidationError.of(e.getPosition(), e.getMessage(), e.getToken()));
        } catch (Exception e) {
            errors.add(ValidationError.of(-1, e.getMessage(), ""));
        }
        return errors;
    }

    // ── Rule validation ───────────────────────────────────────────────────────

    /**
     * Validates both expressions of a {@link DslRule} (syntax only, no access level check).
     *
     * @param rule the rule to validate
     * @return list of all validation errors; empty if fully valid
     */
    public List<ValidationError> validateRule(DslRule rule) {
        List<ValidationError> errors = new ArrayList<>();
        if (rule.getName() == null || rule.getName().isBlank()) {
            errors.add(ValidationError.of("Rule name must not be blank"));
        }
        errors.addAll(validateCondition(rule.getConditionExpression()));
        errors.addAll(validateAction(rule.getActionExpression()));
        return errors;
    }

    /**
     * Validates a {@link DslRule} with access level restrictions.
     *
     * <p>Performs all checks of {@link #validateRule(DslRule)} plus the
     * access level check on the condition expression.
     *
     * @param rule        the rule to validate
     * @param accessLevel the access level to enforce
     * @return list of all validation errors; empty if fully valid
     * @throws UnsupportedDslAccessException if {@code accessLevel == NONE}
     */
    public List<ValidationError> validateRuleWithAccessLevel(DslRule rule,
                                                              DslAccessLevel accessLevel) {
        List<ValidationError> errors = new ArrayList<>();
        if (rule.getName() == null || rule.getName().isBlank()) {
            errors.add(ValidationError.of("Rule name must not be blank"));
        }
        // Validate condition with access level
        errors.addAll(validateConditionWithAccessLevel(
                rule.getConditionExpression(), accessLevel));
        // Validate action (no access level restriction on actions)
        errors.addAll(validateAction(rule.getActionExpression()));
        return errors;
    }
}
