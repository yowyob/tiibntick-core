package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer;

/**
 * A single lexical token produced by the {@link DslLexer}.
 *
 * @param type     the category of this token
 * @param value    the raw text that produced this token
 * @param position zero-based character index in the input where this token starts
 *
 * @author MANFOUO Braun
 */
public record Token(TokenType type, String value, int position) {

    public static Token of(TokenType type, String value, int position) {
        return new Token(type, value, position);
    }

    public boolean is(TokenType t) {
        return this.type == t;
    }

    @Override
    public String toString() {
        return "Token[" + type + " '" + value + "' @" + position + "]";
    }
}
