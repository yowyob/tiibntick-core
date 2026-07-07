package com.yowyob.tiibntick.core.billing.dsl.application.service;

import com.yowyob.tiibntick.core.billing.dsl.domain.exception.UnsupportedDslAccessException;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAccessLevel;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.ValidationError;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates that a compiled DSL expression (AST tree) only uses variables
 * and operators permitted for the given {@link DslAccessLevel}.
 *
 * <h3>Access level rules</h3>
 * <ul>
 *   <li>{@link DslAccessLevel#FULL} — no restriction on variables, operators,
 *       or nesting depth.</li>
 *   <li>{@link DslAccessLevel#SIMPLIFIED} — restricted variable set,
 *       max {@value #MAX_NESTING_SIMPLIFIED} nesting levels,
 *       max {@value #MAX_RULES_SIMPLIFIED} rules per policy.</li>
 *   <li>{@link DslAccessLevel#NONE} — throws
 *       {@link UnsupportedDslAccessException} immediately.</li>
 * </ul>
 *
 * <h3>SIMPLIFIED — allowed variables</h3>
 * Basic delivery metrics, common parcel attributes, and temporal context.
 * Technical FreelancerOrg variables (vehicleType, activeEquipmentTypes) and
 * infrastructure variables (networkHops, storageHours) are not available.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
public class DslAccessValidator {

    /** Maximum nesting depth for SIMPLIFIED access level. */
    public static final int MAX_NESTING_SIMPLIFIED = 3;

    /** Maximum number of rules per policy for SIMPLIFIED access. */
    public static final int MAX_RULES_SIMPLIFIED = 20;

    /**
     * Variables allowed for {@link DslAccessLevel#SIMPLIFIED} access.
     * Any variable not in this set will cause a validation error.
     */
    private static final Set<String> SIMPLIFIED_ALLOWED_VARIABLES = Set.of(
            // Core delivery metrics
            "weight",
            "distance",
            // Package context
            "packagetype",
            "priority",
            "packagecount",
            "requiresrefrigeration",
            "requiresassembly",
            "requiresidcheck",
            "deliveryattempt",
            "attemptnumber",
            // Client context
            "clienttxcount",
            "txcount",
            "isrecurringclient",
            "clientsegment",
            "paymentmethod",
            // Temporal context
            "timeofday",
            "dayofweek",
            "isholiday",
            "ispublicholiday",
            "isweekend",
            "isweekday",
            "isnonworkingday",
            // Environmental context
            "weather",
            "roadtype",
            "israining",
            "isroaddegraded",
            // Zone context (read-only, no deep FreelancerOrg specifics)
            "zonetype",
            "deliveryzonetype",
            "zonedifficulty",
            "zoneaccessdifficulty"
    );

    /**
     * Variables restricted to {@link DslAccessLevel#FULL} access only.
     * Attempting to use these in SIMPLIFIED mode causes a validation error.
     */
    private static final Set<String> FULL_ONLY_VARIABLES = Set.of(
            // FreelancerOrg fleet variables
            "vehicletype",
            "activeequipmenttypes",
            "equipmenttypes",
            "specialization",
            "issubdeliverer",
            // Financial/declared value
            "declaredvalue",
            // Infrastructure variables
            "storagehours",
            "networkhops",
            "networkhopcount",
            // Policy owner context
            "policyownertype"
    );

    /**
     * Validates that the given AST only uses variables and constructs allowed
     * for the specified access level.
     *
     * @param root        the root of the compiled condition AST
     * @param accessLevel the access level to enforce
     * @return list of validation errors; empty if the expression is valid for the level
     * @throws UnsupportedDslAccessException if {@code accessLevel == NONE}
     */
    public List<ValidationError> validate(AstNode root, DslAccessLevel accessLevel) {
        if (accessLevel == DslAccessLevel.NONE) {
            throw new UnsupportedDslAccessException(accessLevel,
                    "DSL authoring is not available for this actor type");
        }
        if (accessLevel == DslAccessLevel.FULL) {
            return List.of(); // No restrictions
        }
        // SIMPLIFIED — check variables and nesting depth
        List<ValidationError> errors = new ArrayList<>();
        checkVariablesRecursive(root, accessLevel, errors);
        int depth = computeNestingDepth(root);
        if (depth > MAX_NESTING_SIMPLIFIED) {
            errors.add(ValidationError.of(
                    "Expression nesting depth " + depth + " exceeds the maximum of "
                    + MAX_NESTING_SIMPLIFIED + " allowed for "
                    + DslAccessLevel.SIMPLIFIED + " access."));
        }
        return errors;
    }

    /**
     * Validates the rule count against the SIMPLIFIED limit.
     *
     * @param ruleCount the current number of rules for the policy
     * @param accessLevel the access level to enforce
     * @return list of validation errors (empty if within limits)
     */
    public List<ValidationError> validateRuleCount(int ruleCount, DslAccessLevel accessLevel) {
        if (accessLevel != DslAccessLevel.SIMPLIFIED) return List.of();
        if (ruleCount > MAX_RULES_SIMPLIFIED) {
            return List.of(ValidationError.of(
                    "Policy has " + ruleCount + " rules which exceeds the maximum of "
                    + MAX_RULES_SIMPLIFIED + " allowed for " + DslAccessLevel.SIMPLIFIED + " access."));
        }
        return List.of();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void checkVariablesRecursive(AstNode node, DslAccessLevel level,
                                          List<ValidationError> errors) {
        if (node instanceof VariableNode varNode) {
            String name = varNode.getVariableName().toLowerCase();
            if (FULL_ONLY_VARIABLES.contains(name)) {
                errors.add(ValidationError.of(
                        "Variable '" + varNode.getVariableName() + "' requires "
                        + DslAccessLevel.FULL + " access but the current level is "
                        + level + ". Use one of: " + SIMPLIFIED_ALLOWED_VARIABLES));
            }
            return;
        }
        if (node instanceof BinaryOpNode binOp) {
            checkVariablesRecursive(binOp.getLeft(), level, errors);
            checkVariablesRecursive(binOp.getRight(), level, errors);
            return;
        }
        if (node instanceof UnaryOpNode unOp) {
            checkVariablesRecursive(unOp.getOperand(), level, errors);
            return;
        }
        if (node instanceof InListNode inList) {
            checkVariablesRecursive(inList.getVariable(), level, errors);
            return;
        }
        if (node instanceof BetweenNode between) {
            checkVariablesRecursive(between.getVariable(), level, errors);
            return;
        }
        if (node instanceof ContainsNode containsNode) {
            // CONTAINS operator is restricted to FULL
            if (level == DslAccessLevel.SIMPLIFIED) {
                errors.add(ValidationError.of(
                        "The CONTAINS operator (used with '" + containsNode.getListVariableName()
                        + "') requires " + DslAccessLevel.FULL + " access. "
                        + "It is not available in " + DslAccessLevel.SIMPLIFIED + " mode."));
            }
            return;
        }
        if (node instanceof DayIsNode) {
            // DayIs is allowed in SIMPLIFIED
            return;
        }
        if (node instanceof TimeIsBetweenNode) {
            // TimeIsBetween is allowed in SIMPLIFIED
            return;
        }
        // LiteralNode: no restriction
    }

    /**
     * Computes the maximum nesting depth of AND/OR nodes in the AST.
     *
     * @param node the root node
     * @return maximum nesting depth (1 = flat, 2 = one level of nesting, etc.)
     */
    private int computeNestingDepth(AstNode node) {
        if (node instanceof BinaryOpNode binOp) {
            // Only AND/OR count as logical nesting
            if (binOp.getOperator() == BinaryOperator.AND
                    || binOp.getOperator() == BinaryOperator.OR) {
                return 1 + Math.max(
                        computeNestingDepth(binOp.getLeft()),
                        computeNestingDepth(binOp.getRight()));
            }
            return 1;
        }
        if (node instanceof UnaryOpNode unOp) {
            return 1 + computeNestingDepth(unOp.getOperand());
        }
        return 1; // leaf or non-nesting node
    }
}
