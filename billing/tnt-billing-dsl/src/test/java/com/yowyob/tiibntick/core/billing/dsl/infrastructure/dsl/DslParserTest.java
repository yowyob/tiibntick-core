package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl;

import com.yowyob.tiibntick.core.billing.dsl.domain.exception.DslParseException;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAction;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslActionType;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast.AstNode;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.DslLexer;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.parser.DslParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DslParser}.
 * Validates that the parser produces correct AST structures.
 *
 * @author MANFOUO Braun
 */
@DisplayName("DslParser — AST construction tests")
class DslParserTest {

    private DslLexer lexer;
    private DslParser parser;

    @BeforeEach
    void setUp() {
        lexer = new DslLexer();
        parser = new DslParser();
    }

    private AstNode parseCondition(String expr) {
        return parser.parseCondition(lexer.tokenize(expr));
    }

    private List<DslAction> parseActions(String expr) {
        return parser.parseActions(lexer.tokenize(expr));
    }

    @Test
    @DisplayName("should parse simple comparison: weight <= 5")
    void testSimpleComparison() {
        AstNode node = parseCondition("weight <= 5");
        assertThat(node).isNotNull();
        assertThat(node.nodeType()).contains("BinaryOp");
    }

    @Test
    @DisplayName("should parse AND expression and return correct nodeType")
    void testAndExpression() {
        AstNode node = parseCondition("weight <= 5 AND distance <= 10");
        assertThat(node.nodeType()).contains("AND");
    }

    @Test
    @DisplayName("should parse OR expression")
    void testOrExpression() {
        AstNode node = parseCondition("priority == 'HIGH' OR priority == 'URGENT'");
        assertThat(node.nodeType()).contains("OR");
    }

    @Test
    @DisplayName("should parse NOT expression")
    void testNotExpression() {
        AstNode node = parseCondition("NOT isRaining == true");
        assertThat(node.nodeType()).contains("Unary");
    }

    @Test
    @DisplayName("should parse IN list expression")
    void testInList() {
        AstNode node = parseCondition("packageType IN [FRAGILE, ELECTRONICS]");
        assertThat(node.nodeType()).contains("InList");
    }

    @Test
    @DisplayName("should parse BETWEEN expression")
    void testBetween() {
        AstNode node = parseCondition("distance BETWEEN 5 AND 15");
        assertThat(node.nodeType()).contains("Between");
    }

    @Test
    @DisplayName("should parse parenthesised expression")
    void testParenthesised() {
        AstNode node = parseCondition("(weight <= 5 AND distance <= 10) OR priority == 'HIGH'");
        assertThat(node.nodeType()).contains("OR");
    }

    @Test
    @DisplayName("should parse action SET_BASE 1000 XAF")
    void testActionSetBase() {
        List<DslAction> actions = parseActions("SET_BASE 1000 XAF");
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).getActionType()).isEqualTo(DslActionType.SET_BASE);
        assertThat(actions.get(0).getValue()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(actions.get(0).getCurrencyCode()).isEqualTo("XAF");
    }

    @Test
    @DisplayName("should parse action SET_PER_KM 50 XAF")
    void testActionSetPerKm() {
        List<DslAction> actions = parseActions("SET_PER_KM 50 XAF");
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).getActionType()).isEqualTo(DslActionType.SET_PER_KM);
        assertThat(actions.get(0).getValue()).isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    @DisplayName("should parse multiple actions: SET_BASE + SET_PER_KM")
    void testMultipleActions() {
        List<DslAction> actions = parseActions("SET_BASE 1000 XAF SET_PER_KM 50 XAF");
        assertThat(actions).hasSize(2);
        assertThat(actions.get(0).getActionType()).isEqualTo(DslActionType.SET_BASE);
        assertThat(actions.get(1).getActionType()).isEqualTo(DslActionType.SET_PER_KM);
    }

    @Test
    @DisplayName("should parse DISCOUNT_PCT 5 action")
    void testActionDiscountPct() {
        List<DslAction> actions = parseActions("DISCOUNT_PCT 5");
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).getActionType()).isEqualTo(DslActionType.DISCOUNT_PCT);
        assertThat(actions.get(0).getValue()).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    @DisplayName("should parse ADD_PCT 15 action")
    void testActionAddPct() {
        List<DslAction> actions = parseActions("ADD_PCT 15");
        assertThat(actions.get(0).getActionType()).isEqualTo(DslActionType.ADD_PCT);
        assertThat(actions.get(0).getValue()).isEqualByComparingTo(new BigDecimal("15"));
    }

    @Test
    @DisplayName("should throw DslParseException for invalid condition")
    void testInvalidCondition() {
        assertThatThrownBy(() -> parseCondition("weight <= "))
                .isInstanceOf(DslParseException.class);
    }

    @Test
    @DisplayName("should throw DslParseException for unmatched parenthesis")
    void testUnmatchedParenthesis() {
        assertThatThrownBy(() -> parseCondition("(weight <= 5 AND distance <= 10"))
                .isInstanceOf(DslParseException.class);
    }
}
