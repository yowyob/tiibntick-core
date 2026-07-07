package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer;

import com.yowyob.tiibntick.core.billing.dsl.domain.exception.DslParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stateless lexer that converts a raw DSL expression string into a list of {@link Token}s.
 *
 * <p>The lexer is deliberately simple and single-pass (O(n)) without backtracking.
 * Whitespace is silently skipped. All keywords are case-insensitive.</p>
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>Time literals in {@code HH:MM} format are recognized and emitted as
 *       {@link TokenType#TIME_LITERAL} tokens (e.g. {@code 22:00}, {@code 06:30}).</li>
 *   <li>New keywords: {@code CONTAINS}, {@code DAY_IS}, {@code TIME_IS_BETWEEN}.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public class DslLexer {

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("and",             TokenType.AND),
            Map.entry("or",              TokenType.OR),
            Map.entry("not",             TokenType.NOT),
            Map.entry("in",              TokenType.IN),
            Map.entry("between",         TokenType.BETWEEN),
            Map.entry("true",            TokenType.TRUE),
            Map.entry("false",           TokenType.FALSE),
            //  — new operator keywords
            Map.entry("contains",        TokenType.CONTAINS),
            Map.entry("day_is",          TokenType.DAY_IS),
            Map.entry("time_is_between", TokenType.TIME_IS_BETWEEN),
            // Action keywords
            Map.entry("action",          TokenType.ACTION),
            Map.entry("set_base",        TokenType.SET_BASE),
            Map.entry("add_fixed",       TokenType.ADD_FIXED),
            Map.entry("add_pct",         TokenType.ADD_PCT),
            Map.entry("discount_pct",    TokenType.DISCOUNT_PCT),
            Map.entry("discount_fixed",  TokenType.DISCOUNT_FIXED),
            Map.entry("set_per_km",      TokenType.SET_PER_KM),
            Map.entry("set_per_kg",      TokenType.SET_PER_KG)
    );

    /**
     * Tokenises the given DSL expression.
     *
     * @param expression the raw DSL text (condition or action)
     * @return ordered list of tokens (the last element is always {@link TokenType#EOF})
     * @throws DslParseException if an unrecognised character is encountered
     */
    public List<Token> tokenize(String expression) {
        List<Token> tokens = new ArrayList<>();
        if (expression == null || expression.isBlank()) {
            tokens.add(Token.of(TokenType.EOF, "", 0));
            return tokens;
        }

        char[] chars = expression.toCharArray();
        int i = 0;

        while (i < chars.length) {
            // Skip whitespace
            if (Character.isWhitespace(chars[i])) {
                i++;
                continue;
            }

            int start = i;

            // Number literal or negative number
            if (Character.isDigit(chars[i])
                    || (chars[i] == '-' && i + 1 < chars.length && Character.isDigit(chars[i + 1]))) {
                StringBuilder sb = new StringBuilder();
                if (chars[i] == '-') sb.append(chars[i++]);
                while (i < chars.length && (Character.isDigit(chars[i]) || chars[i] == '.')) {
                    sb.append(chars[i++]);
                }
                //  — Detect time literal HH:MM immediately following a number
                // e.g. "22:00" → the "22" is consumed above; now look for ":MM"
                if (i < chars.length && chars[i] == ':' && i + 2 < chars.length
                        && Character.isDigit(chars[i + 1]) && Character.isDigit(chars[i + 2])) {
                    // consume :MM
                    sb.append(chars[i++]); // ':'
                    sb.append(chars[i++]); // first digit
                    sb.append(chars[i++]); // second digit
                    // optionally consume :SS if present (e.g. 22:00:00)
                    if (i < chars.length && chars[i] == ':' && i + 2 < chars.length
                            && Character.isDigit(chars[i + 1]) && Character.isDigit(chars[i + 2])) {
                        sb.append(chars[i++]);
                        sb.append(chars[i++]);
                        sb.append(chars[i++]);
                    }
                    tokens.add(Token.of(TokenType.TIME_LITERAL, sb.toString(), start));
                } else {
                    tokens.add(Token.of(TokenType.NUMBER, sb.toString(), start));
                }
                continue;
            }

            // String / enum literal in quotes
            if (chars[i] == '\'' || chars[i] == '"') {
                char quote = chars[i++];
                StringBuilder sb = new StringBuilder();
                while (i < chars.length && chars[i] != quote) {
                    sb.append(chars[i++]);
                }
                if (i >= chars.length) {
                    throw new DslParseException("Unterminated string literal", start, sb.toString());
                }
                i++; // consume closing quote
                tokens.add(Token.of(TokenType.STRING, sb.toString(), start));
                continue;
            }

            // Identifier or keyword (letters, digits, underscore)
            if (Character.isLetter(chars[i]) || chars[i] == '_') {
                StringBuilder sb = new StringBuilder();
                while (i < chars.length && (Character.isLetterOrDigit(chars[i]) || chars[i] == '_')) {
                    sb.append(chars[i++]);
                }
                String word = sb.toString();
                TokenType kw = KEYWORDS.get(word.toLowerCase());
                tokens.add(Token.of(kw != null ? kw : TokenType.IDENTIFIER, word, start));
                continue;
            }

            // Two-character operators
            if (i + 1 < chars.length) {
                String two = "" + chars[i] + chars[i + 1];
                switch (two) {
                    case "==" -> { tokens.add(Token.of(TokenType.EQ,  two, start)); i += 2; continue; }
                    case "!=" -> { tokens.add(Token.of(TokenType.NEQ, two, start)); i += 2; continue; }
                    case "<=" -> { tokens.add(Token.of(TokenType.LTE, two, start)); i += 2; continue; }
                    case ">=" -> { tokens.add(Token.of(TokenType.GTE, two, start)); i += 2; continue; }
                    default   -> { /* fall through to single-char */ }
                }
            }

            // Single-character operators and delimiters
            Token single = switch (chars[i]) {
                case '<' -> Token.of(TokenType.LT, "<", start);
                case '>' -> Token.of(TokenType.GT, ">", start);
                case '(' -> Token.of(TokenType.LPAREN, "(", start);
                case ')' -> Token.of(TokenType.RPAREN, ")", start);
                case '[' -> Token.of(TokenType.LBRACKET, "[", start);
                case ']' -> Token.of(TokenType.RBRACKET, "]", start);
                case ',' -> Token.of(TokenType.COMMA, ",", start);
                default  -> throw new DslParseException(
                        "Unexpected character '" + chars[i] + "'", start, String.valueOf(chars[i]));
            };
            tokens.add(single);
            i++;
        }

        tokens.add(Token.of(TokenType.EOF, "", chars.length));
        return tokens;
    }
}
