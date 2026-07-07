package com.yowyob.tiibntick.core.billing.dsl.application.service;

import com.yowyob.tiibntick.core.billing.dsl.domain.exception.DslParseException;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAction;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslRule;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast.AstNode;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.DslLexer;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.Token;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.parser.DslParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Compiles a raw {@link DslRule} (with text expressions) into an in-memory
 * representation that contains the parsed AST and action list.
 * <p>
 * The compiled representation is held in the rule's {@code actions} field and
 * a transient AST cache maintained by the {@link PricingEngine}.
 * </p>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DslCompilerService {

    private final DslLexer lexer;
    private final DslParser parser;

    /**
     * Compiles both the condition expression and the action expression of the given rule.
     * Returns a new {@link DslRule} instance enriched with compiled actions.
     *
     * @param rule the raw rule to compile
     * @return a new compiled DslRule
     * @throws DslParseException if syntax errors are detected
     */
    public DslRule compile(DslRule rule) {
        log.debug("Compiling DSL rule '{}' (id={})", rule.getName(), rule.getId());

        // Compile condition — validates syntax
        compileCondition(rule.getConditionExpression());

        // Compile actions
        List<DslAction> actions = compileActions(rule.getActionExpression());

        return rule.toBuilder()
                .actions(actions)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Parses and returns the AST for a condition expression (used by {@link DslValidatorService}).
     *
     * @param conditionExpression raw condition DSL text
     * @return parsed AST root node
     * @throws DslParseException on syntax error
     */
    public AstNode compileCondition(String conditionExpression) {
        List<Token> tokens = lexer.tokenize(conditionExpression);
        return parser.parseCondition(tokens);
    }

    /**
     * Parses and returns the list of {@link DslAction} from an action expression.
     *
     * @param actionExpression raw action DSL text
     * @return parsed action list
     * @throws DslParseException on syntax error
     */
    public List<DslAction> compileActions(String actionExpression) {
        List<Token> tokens = lexer.tokenize(actionExpression);
        return parser.parseActions(tokens);
    }

    /**
     * Creates a minimal DslRule from individual expressions, assigns a new UUID,
     * and compiles it.
     *
     * @param name                rule name
     * @param conditionExpression condition DSL text
     * @param actionExpression    action DSL text
     * @param priority            evaluation priority
     * @param tenantId            owner tenant
     * @param policyId            parent policy
     * @return compiled DslRule
     */
    public DslRule createAndCompile(
            String name,
            String conditionExpression,
            String actionExpression,
            int priority,
            UUID tenantId,
            UUID policyId) {
        DslRule draft = DslRule.builder()
                .id(UUID.randomUUID())
                .name(name)
                .conditionExpression(conditionExpression)
                .actionExpression(actionExpression)
                .priority(priority)
                .active(true)
                .tenantId(tenantId)
                .policyId(policyId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return compile(draft);
    }
}
