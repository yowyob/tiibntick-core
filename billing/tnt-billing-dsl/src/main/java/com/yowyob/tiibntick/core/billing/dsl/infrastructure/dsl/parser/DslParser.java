package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.parser;

import com.yowyob.tiibntick.core.billing.dsl.domain.exception.DslParseException;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAction;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslActionType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslVariableType;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.ast.*;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.Token;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.TokenType;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser that converts a token stream produced by
 * {@link com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.DslLexer}
 * into an {@link AstNode} tree for condition expressions, and a
 * {@link List} of {@link DslAction} objects for action expressions.
 *
 * <h3>Grammar (simplified EBNF)</h3>
 * <pre>
 * expression      ::= orExpr
 * orExpr          ::= andExpr ('OR' andExpr)*
 * andExpr         ::= notExpr ('AND' notExpr)*
 * notExpr         ::= 'NOT' primary | primary
 * primary         ::= '(' expression ')'
 *                   | identifier 'IN' '[' list ']'
 *                   | identifier 'BETWEEN' number 'AND' number
 *                   | identifier 'CONTAINS' literal              ()
 *                   | identifier 'DAY_IS' literal                ()
 *                   | identifier 'TIME_IS_BETWEEN' time 'AND' time ()
 *                   | identifier operator literal
 * operator        ::= '==' | '!=' | '<' | '<=' | '>' | '>='
 * literal         ::= number | string | timeLiteral
 * timeLiteral     ::= HH:MM | HH:MM:SS                          ()
 * </pre>
 *
 * <h3>Action grammar</h3>
 * <pre>
 * actionList ::= action*
 * action     ::= actionType number [currencyCode]
 * actionType ::= 'SET_BASE' | 'ADD_FIXED' | 'ADD_PCT' | 'DISCOUNT_PCT'
 *              | 'DISCOUNT_FIXED' | 'SET_PER_KM' | 'SET_PER_KG'
 * </pre>
 *
 * @author MANFOUO Braun
 */
public class DslParser {

    private List<Token> tokens;
    private int pos;

    // ─────────────────────────────── CONDITION PARSER ────────────────────────

    /**
     * Parses a condition expression and returns the root AST node.
     *
     * @param tokens token stream (must end with EOF)
     * @return root AstNode representing the boolean condition
     */
    public AstNode parseCondition(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
        AstNode result = parseOrExpr();
        if (!peek().is(TokenType.EOF)) {
            Token t = peek();
            throw new DslParseException(
                    "Unexpected token after condition", t.position(), t.value());
        }
        return result;
    }

    private AstNode parseOrExpr() {
        AstNode left = parseAndExpr();
        while (peek().is(TokenType.OR)) {
            consume(TokenType.OR);
            AstNode right = parseAndExpr();
            left = new BinaryOpNode(left, BinaryOperator.OR, right);
        }
        return left;
    }

    private AstNode parseAndExpr() {
        AstNode left = parseNotExpr();
        while (peek().is(TokenType.AND)) {
            consume(TokenType.AND);
            AstNode right = parseNotExpr();
            left = new BinaryOpNode(left, BinaryOperator.AND, right);
        }
        return left;
    }

    private AstNode parseNotExpr() {
        if (peek().is(TokenType.NOT)) {
            consume(TokenType.NOT);
            return new UnaryOpNode("NOT", parsePrimary());
        }
        return parsePrimary();
    }

    private AstNode parsePrimary() {
        // Parenthesised sub-expression
        if (peek().is(TokenType.LPAREN)) {
            consume(TokenType.LPAREN);
            AstNode inner = parseOrExpr();
            consume(TokenType.RPAREN);
            return inner;
        }

        // Boolean literals
        if (peek().is(TokenType.TRUE)) {
            consume(TokenType.TRUE);
            return new LiteralNode(Boolean.TRUE, DslVariableType.BOOLEAN);
        }
        if (peek().is(TokenType.FALSE)) {
            consume(TokenType.FALSE);
            return new LiteralNode(Boolean.FALSE, DslVariableType.BOOLEAN);
        }

        // Must be an identifier
        Token id = consume(TokenType.IDENTIFIER);
        VariableNode variable = new VariableNode(id.value());

        // identifier IN [...]
        if (peek().is(TokenType.IN)) {
            consume(TokenType.IN);
            consume(TokenType.LBRACKET);
            List<LiteralNode> candidates = new ArrayList<>();
            candidates.add(parseLiteral());
            while (peek().is(TokenType.COMMA)) {
                consume(TokenType.COMMA);
                candidates.add(parseLiteral());
            }
            consume(TokenType.RBRACKET);
            return new InListNode(variable, candidates);
        }

        // identifier BETWEEN low AND high
        if (peek().is(TokenType.BETWEEN)) {
            consume(TokenType.BETWEEN);
            LiteralNode low  = parseNumberLiteral();
            consume(TokenType.AND);
            LiteralNode high = parseNumberLiteral();
            return new BetweenNode(variable, low, high);
        }

        //  — identifier CONTAINS element
        if (peek().is(TokenType.CONTAINS)) {
            consume(TokenType.CONTAINS);
            LiteralNode element = parseLiteral();
            return new ContainsNode(variable, element);
        }

        //  — identifier DAY_IS dayClassification
        if (peek().is(TokenType.DAY_IS)) {
            consume(TokenType.DAY_IS);
            Token dayToken = peek();
            if (!dayToken.is(TokenType.IDENTIFIER) && !dayToken.is(TokenType.STRING)) {
                throw new DslParseException(
                        "Expected day classification after DAY_IS",
                        dayToken.position(), dayToken.value());
            }
            advance();
            return new DayIsNode(variable, dayToken.value().toUpperCase());
        }

        //  — identifier TIME_IS_BETWEEN startTime AND endTime
        if (peek().is(TokenType.TIME_IS_BETWEEN)) {
            consume(TokenType.TIME_IS_BETWEEN);
            LocalTime startTime = parseTimeLiteral();
            consume(TokenType.AND);
            LocalTime endTime   = parseTimeLiteral();
            return new TimeIsBetweenNode(variable, startTime, endTime);
        }

        // identifier operator literal
        BinaryOperator op = parseComparisonOperator();
        LiteralNode literal = parseLiteral();
        return new BinaryOpNode(variable, op, literal);
    }

    private BinaryOperator parseComparisonOperator() {
        Token t = peek();
        return switch (t.type()) {
            case EQ  -> { consume(TokenType.EQ);  yield BinaryOperator.EQ;  }
            case NEQ -> { consume(TokenType.NEQ); yield BinaryOperator.NEQ; }
            case LT  -> { consume(TokenType.LT);  yield BinaryOperator.LT;  }
            case LTE -> { consume(TokenType.LTE); yield BinaryOperator.LTE; }
            case GT  -> { consume(TokenType.GT);  yield BinaryOperator.GT;  }
            case GTE -> { consume(TokenType.GTE); yield BinaryOperator.GTE; }
            default  -> throw new DslParseException(
                    "Expected comparison operator, got: " + t.value(), t.position(), t.value());
        };
    }

    private LiteralNode parseLiteral() {
        Token t = peek();
        if (t.is(TokenType.NUMBER)) {
            return parseNumberLiteral();
        }
        if (t.is(TokenType.TIME_LITERAL)) {
            // Return time literal as a LocalTime wrapped in a LiteralNode
            advance();
            LocalTime lt = parseTime(t.value(), t.position());
            return new LiteralNode(lt, DslVariableType.TIME);
        }
        if (t.is(TokenType.STRING) || t.is(TokenType.IDENTIFIER)) {
            advance();
            return new LiteralNode(t.value().toUpperCase(), DslVariableType.ENUM);
        }
        if (t.is(TokenType.TRUE)) {
            advance();
            return new LiteralNode(Boolean.TRUE, DslVariableType.BOOLEAN);
        }
        if (t.is(TokenType.FALSE)) {
            advance();
            return new LiteralNode(Boolean.FALSE, DslVariableType.BOOLEAN);
        }
        throw new DslParseException("Expected literal, got: " + t.value(), t.position(), t.value());
    }

    private LiteralNode parseNumberLiteral() {
        Token t = consume(TokenType.NUMBER);
        try {
            return new LiteralNode(new BigDecimal(t.value()), DslVariableType.NUMBER);
        } catch (NumberFormatException e) {
            throw new DslParseException("Invalid number: " + t.value(), t.position(), t.value());
        }
    }

    /**
     *  — Parses a TIME_LITERAL token and returns the corresponding {@link LocalTime}.
     * Expects HH:MM or HH:MM:SS format.
     */
    private LocalTime parseTimeLiteral() {
        Token t = peek();
        if (t.is(TokenType.TIME_LITERAL)) {
            advance();
            return parseTime(t.value(), t.position());
        }
        // Fallback: might be a NUMBER followed by colon (rare edge case handled in lexer)
        throw new DslParseException(
                "Expected time literal (HH:MM), got: " + t.value(), t.position(), t.value());
    }

    private LocalTime parseTime(String value, int position) {
        try {
            // Support HH:MM and HH:MM:SS
            return switch (value.length()) {
                case 5  -> LocalTime.of(
                        Integer.parseInt(value.substring(0, 2)),
                        Integer.parseInt(value.substring(3, 5)));
                case 8  -> LocalTime.of(
                        Integer.parseInt(value.substring(0, 2)),
                        Integer.parseInt(value.substring(3, 5)),
                        Integer.parseInt(value.substring(6, 8)));
                default -> LocalTime.parse(value);
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new DslParseException(
                    "Invalid time literal: '" + value + "'. Expected HH:MM or HH:MM:SS",
                    position, value);
        }
    }

    // ─────────────────────────────── ACTION PARSER ───────────────────────────

    /**
     * Parses an action expression and returns a list of {@link DslAction}.
     *
     * @param tokens token stream from the action expression
     * @return list of parsed DslAction instances
     */
    public List<DslAction> parseActions(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
        List<DslAction> actions = new ArrayList<>();
        while (!peek().is(TokenType.EOF)) {
            actions.add(parseOneAction());
        }
        return actions;
    }

    private DslAction parseOneAction() {
        Token actionToken = peek();
        DslActionType type = switch (actionToken.type()) {
            case SET_BASE       -> { advance(); yield DslActionType.SET_BASE;       }
            case ADD_FIXED      -> { advance(); yield DslActionType.ADD_FIXED;      }
            case ADD_PCT        -> { advance(); yield DslActionType.ADD_PCT;        }
            case DISCOUNT_PCT   -> { advance(); yield DslActionType.DISCOUNT_PCT;   }
            case DISCOUNT_FIXED -> { advance(); yield DslActionType.DISCOUNT_FIXED; }
            case SET_PER_KM     -> { advance(); yield DslActionType.SET_PER_KM;     }
            case SET_PER_KG     -> { advance(); yield DslActionType.SET_PER_KG;     }
            default -> throw new DslParseException(
                    "Expected action type keyword, got: " + actionToken.value(),
                    actionToken.position(), actionToken.value());
        };

        BigDecimal value = new BigDecimal(consume(TokenType.NUMBER).value());

        // Optional currency code as IDENTIFIER (e.g. XAF, USD)
        String currencyCode = null;
        if (peek().is(TokenType.IDENTIFIER)) {
            currencyCode = advance().value().toUpperCase();
        }

        return DslAction.builder()
                .actionType(type)
                .value(value)
                .currencyCode(currencyCode)
                .build();
    }

    // ─────────────────────────────── HELPERS ─────────────────────────────────

    private Token peek() {
        return tokens.get(pos);
    }

    private Token advance() {
        return tokens.get(pos++);
    }

    private Token consume(TokenType expected) {
        Token t = peek();
        if (!t.is(expected)) {
            throw new DslParseException(
                    "Expected " + expected + " but got " + t.type() + " ('" + t.value() + "')",
                    t.position(), t.value());
        }
        return tokens.get(pos++);
    }
}
