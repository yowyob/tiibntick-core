package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl;

import com.yowyob.tiibntick.core.billing.dsl.domain.exception.DslParseException;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.DslLexer;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.Token;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DslLexer}.
 *
 * @author MANFOUO Braun
 */
@DisplayName("DslLexer — tokenisation tests")
class DslLexerTest {

    private DslLexer lexer;

    @BeforeEach
    void setUp() {
        lexer = new DslLexer();
    }

    @Test
    @DisplayName("should tokenise a simple comparison: weight <= 5")
    void testSimpleComparison() {
        List<Token> tokens = lexer.tokenize("weight <= 5");
        assertThat(tokens).hasSize(4); // IDENTIFIER, LTE, NUMBER, EOF
        assertThat(tokens.get(0).type()).isEqualTo(TokenType.IDENTIFIER);
        assertThat(tokens.get(0).value()).isEqualTo("weight");
        assertThat(tokens.get(1).type()).isEqualTo(TokenType.LTE);
        assertThat(tokens.get(2).type()).isEqualTo(TokenType.NUMBER);
        assertThat(tokens.get(2).value()).isEqualTo("5");
        assertThat(tokens.get(3).type()).isEqualTo(TokenType.EOF);
    }

    @Test
    @DisplayName("should tokenise AND expression: weight <= 5 AND distance <= 10")
    void testAndExpression() {
        List<Token> tokens = lexer.tokenize("weight <= 5 AND distance <= 10");
        assertThat(tokens).hasSize(8);
        assertThat(tokens.get(3).type()).isEqualTo(TokenType.AND);
        assertThat(tokens.get(4).type()).isEqualTo(TokenType.IDENTIFIER);
        assertThat(tokens.get(4).value()).isEqualTo("distance");
    }

    @Test
    @DisplayName("should tokenise OR expression: priority == HIGH OR priority == URGENT")
    void testOrExpression() {
        List<Token> tokens = lexer.tokenize("priority == 'HIGH' OR priority == 'URGENT'");
        assertThat(tokens).extracting(Token::type)
                .containsSequence(
                        TokenType.IDENTIFIER, TokenType.EQ, TokenType.STRING,
                        TokenType.OR,
                        TokenType.IDENTIFIER, TokenType.EQ, TokenType.STRING,
                        TokenType.EOF);
    }

    @Test
    @DisplayName("should tokenise NOT expression")
    void testNotExpression() {
        List<Token> tokens = lexer.tokenize("NOT isRaining == true");
        assertThat(tokens.get(0).type()).isEqualTo(TokenType.NOT);
    }

    @Test
    @DisplayName("should tokenise IN list: packageType IN [FRAGILE, ELECTRONICS]")
    void testInList() {
        List<Token> tokens = lexer.tokenize("packageType IN [FRAGILE, ELECTRONICS]");
        assertThat(tokens).extracting(Token::type)
                .containsSequence(
                        TokenType.IDENTIFIER, TokenType.IN, TokenType.LBRACKET,
                        TokenType.IDENTIFIER, TokenType.COMMA, TokenType.IDENTIFIER,
                        TokenType.RBRACKET, TokenType.EOF);
    }

    @Test
    @DisplayName("should tokenise BETWEEN expression: distance BETWEEN 5 AND 15")
    void testBetween() {
        List<Token> tokens = lexer.tokenize("distance BETWEEN 5 AND 15");
        assertThat(tokens).extracting(Token::type)
                .containsSequence(
                        TokenType.IDENTIFIER, TokenType.BETWEEN, TokenType.NUMBER,
                        TokenType.AND, TokenType.NUMBER, TokenType.EOF);
    }

    @Test
    @DisplayName("should tokenise action expression: SET_BASE 1000 XAF")
    void testActionExpression() {
        List<Token> tokens = lexer.tokenize("SET_BASE 1000 XAF");
        assertThat(tokens.get(0).type()).isEqualTo(TokenType.SET_BASE);
        assertThat(tokens.get(1).type()).isEqualTo(TokenType.NUMBER);
        assertThat(tokens.get(1).value()).isEqualTo("1000");
        assertThat(tokens.get(2).type()).isEqualTo(TokenType.IDENTIFIER);
        assertThat(tokens.get(2).value()).isEqualTo("XAF");
    }

    @Test
    @DisplayName("should tokenise decimal numbers: 3.2")
    void testDecimalNumber() {
        List<Token> tokens = lexer.tokenize("weight <= 3.2");
        assertThat(tokens.get(2).type()).isEqualTo(TokenType.NUMBER);
        assertThat(tokens.get(2).value()).isEqualTo("3.2");
    }

    @Test
    @DisplayName("should handle empty expression")
    void testEmptyExpression() {
        List<Token> tokens = lexer.tokenize("");
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).type()).isEqualTo(TokenType.EOF);
    }

    @Test
    @DisplayName("should handle null expression")
    void testNullExpression() {
        List<Token> tokens = lexer.tokenize(null);
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).type()).isEqualTo(TokenType.EOF);
    }

    @Test
    @DisplayName("should throw DslParseException for unknown character")
    void testUnknownCharacter() {
        assertThatThrownBy(() -> lexer.tokenize("weight @ 5"))
                .isInstanceOf(DslParseException.class);
    }

    @Test
    @DisplayName("should throw DslParseException for unterminated string")
    void testUnterminatedString() {
        assertThatThrownBy(() -> lexer.tokenize("priority == 'HIGH"))
                .isInstanceOf(DslParseException.class);
    }

    @Test
    @DisplayName("should be case-insensitive for keywords: and, or, not")
    void testCaseInsensitiveKeywords() {
        List<Token> tokens = lexer.tokenize("weight <= 5 and distance <= 10");
        assertThat(tokens.get(3).type()).isEqualTo(TokenType.AND);
    }

    @Test
    @DisplayName("should tokenise complex rule: fragile high-priority night")
    void testComplexRule() {
        String expr = "weight <= 5 AND packageType == 'FRAGILE' AND priority IN [HIGH, URGENT]";
        List<Token> tokens = lexer.tokenize(expr);
        assertThat(tokens).isNotEmpty();
        assertThat(tokens.get(tokens.size() - 1).type()).isEqualTo(TokenType.EOF);
    }
}
