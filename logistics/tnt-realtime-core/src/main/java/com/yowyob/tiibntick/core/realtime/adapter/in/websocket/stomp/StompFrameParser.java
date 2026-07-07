package com.yowyob.tiibntick.core.realtime.adapter.in.websocket.stomp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Parser for STOMP 1.2 protocol frames received over WebSocket.
 *
 * <p>STOMP 1.2 frame grammar (simplified):</p>
 * <pre>
 * frame        = command LF *( header LF ) LF *OCTET NULL
 * command      = client-commands | server-commands
 * header       = header-name ":" header-value
 * NULL         = &lt;US-ASCII null (octet 0)&gt;
 * </pre>
 *
 * <p>This parser also handles the WebSocket STOMP heartbeat frames
 * (newline-only messages).</p>
 *
 * @author MANFOUO Braun
 */
public final class StompFrameParser {

    private static final Logger log = LoggerFactory.getLogger(StompFrameParser.class);
    private static final char NULL_BYTE = '\u0000';

    private StompFrameParser() {}

    /**
     * Parses a raw text message from a WebSocket session into a {@link StompFrame}.
     *
     * @param rawMessage the raw WebSocket text message payload
     * @return a parsed StompFrame, or a HEARTBEAT frame for empty/newline-only messages
     */
    public static StompFrame parse(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank() || rawMessage.equals("\n") || rawMessage.equals("\r\n")) {
            return StompFrame.heartbeat();
        }

        // Remove the trailing NULL byte (^@) that terminates STOMP frames
        String message = rawMessage.replace(String.valueOf(NULL_BYTE), "").trim();

        String[] sections = message.split("\n\n", 2);
        if (sections.length < 1) {
            log.warn("Malformed STOMP frame: missing header section");
            return StompFrame.of(StompCommand.UNKNOWN, Map.of(), "");
        }

        String headerSection = sections[0];
        String body = sections.length > 1 ? sections[1].trim() : "";

        String[] headerLines = headerSection.split("\n");
        if (headerLines.length == 0) {
            log.warn("Malformed STOMP frame: empty header section");
            return StompFrame.of(StompCommand.UNKNOWN, Map.of(), body);
        }

        // First line is the command
        StompCommand command = parseCommand(headerLines[0].trim());

        // Remaining lines are headers
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < headerLines.length; i++) {
            String line = headerLines[i];
            if (line.isBlank()) continue;
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                String name = line.substring(0, colonIdx).trim().toLowerCase();
                String value = line.substring(colonIdx + 1).trim();
                // STOMP 1.2: first occurrence of a header takes precedence
                headers.putIfAbsent(name, value);
            }
        }

        return StompFrame.of(command, headers, body);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private static StompCommand parseCommand(String commandStr) {
        try {
            return StompCommand.valueOf(commandStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.debug("Unknown STOMP command: '{}'", commandStr);
            return StompCommand.UNKNOWN;
        }
    }
}
