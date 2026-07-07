package com.yowyob.tiibntick.core.billing.dsl.domain.exception;

/**
 * Thrown when the DSL Lexer or Parser encounters a syntax error.
 *
 * @author MANFOUO Braun
 */
public class DslParseException extends RuntimeException {

    private final int position;
    private final String token;

    public DslParseException(String message, int position, String token) {
        super("DSL parse error at position " + position + " (token='" + token + "'): " + message);
        this.position = position;
        this.token = token;
    }

    public DslParseException(String message) {
        super(message);
        this.position = -1;
        this.token = "";
    }

    public int getPosition() {
        return position;
    }

    public String getToken() {
        return token;
    }
}
