package com.yowyob.tiibntick.core.realtime.adapter.in.websocket.stomp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable representation of a parsed STOMP protocol frame.
 *
 * <p>STOMP frame structure:</p>
 * <pre>
 * COMMAND\n
 * header1:value1\n
 * header2:value2\n
 * \n
 * body^@
 * </pre>
 *
 * @author MANFOUO Braun
 */
public final class StompFrame {

    private final StompCommand command;
    private final Map<String, String> headers;
    private final String body;

    private StompFrame(StompCommand command, Map<String, String> headers, String body) {
        this.command = command;
        this.headers = Collections.unmodifiableMap(new HashMap<>(headers));
        this.body = body != null ? body : "";
    }

    public static StompFrame of(StompCommand command, Map<String, String> headers, String body) {
        return new StompFrame(command, headers, body);
    }

    public static StompFrame heartbeat() {
        return new StompFrame(StompCommand.HEARTBEAT, Collections.emptyMap(), "");
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public StompCommand getCommand() {
        return command;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public Optional<String> getHeader(String name) {
        return Optional.ofNullable(headers.get(name));
    }

    public String getHeaderOrDefault(String name, String defaultValue) {
        return headers.getOrDefault(name, defaultValue);
    }

    public boolean hasBody() {
        return body != null && !body.isBlank();
    }

    // ── Convenience for common headers ────────────────────────────────────────

    public Optional<String> getDestination() {
        return getHeader("destination");
    }

    public Optional<String> getLogin() {
        return getHeader("login");
    }

    public Optional<String> getPasscode() {
        return getHeader("passcode");
    }

    public Optional<String> getSubscriptionId() {
        return getHeader("id");
    }

    public Optional<String> getContentType() {
        return getHeader("content-type");
    }

    public Optional<String> getReceiptId() {
        return getHeader("receipt");
    }

    @Override
    public String toString() {
        return "StompFrame{command=" + command + ", headers=" + headers + "}";
    }
}
