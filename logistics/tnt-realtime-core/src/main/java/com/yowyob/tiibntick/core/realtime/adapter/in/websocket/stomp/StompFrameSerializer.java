package com.yowyob.tiibntick.core.realtime.adapter.in.websocket.stomp;

import java.util.Map;

/**
 * Serializes server-side STOMP frames to their wire format (text string).
 * Used when the server sends MESSAGE frames to subscribed clients.
 *
 * @author MANFOUO Braun
 */
public final class StompFrameSerializer {

    private static final char NULL_BYTE = '\u0000';
    private static final char LF = '\n';

    private StompFrameSerializer() {}

    /**
     * Builds a STOMP CONNECTED frame (sent after a successful CONNECT).
     *
     * @param version    the negotiated STOMP version (e.g. "1.2")
     * @param sessionId  the assigned session identifier
     * @param heartbeat  the negotiated heartbeat values (e.g. "0,10000")
     * @return serialized CONNECTED frame
     */
    public static String connected(String version, String sessionId, String heartbeat) {
        return buildFrame("CONNECTED",
                Map.of("version", version, "session", sessionId, "heart-beat", heartbeat),
                "");
    }

    /**
     * Builds a STOMP MESSAGE frame for broadcasting to subscribers.
     *
     * @param destination  the topic destination path
     * @param subscriptionId the recipient's subscription ID
     * @param messageId    a unique message identifier
     * @param contentType  the content type (e.g. "application/json")
     * @param body         the message body (JSON string)
     * @return serialized MESSAGE frame
     */
    public static String message(String destination, String subscriptionId,
                                  String messageId, String contentType, String body) {
        return buildFrame("MESSAGE",
                Map.of(
                        "destination", destination,
                        "subscription", subscriptionId,
                        "message-id", messageId,
                        "content-type", contentType,
                        "content-length", String.valueOf(body.getBytes().length)
                ),
                body);
    }

    /**
     * Builds a STOMP ERROR frame.
     *
     * @param message      the error message
     * @param receiptId    the receipt-id from the offending client frame (may be null)
     * @return serialized ERROR frame
     */
    public static String error(String message, String receiptId) {
        Map<String, String> headers = receiptId != null
                ? Map.of("message", message, "receipt-id", receiptId)
                : Map.of("message", message);
        return buildFrame("ERROR", headers, message);
    }

    /**
     * Builds a STOMP RECEIPT frame (acknowledgment of a client request).
     *
     * @param receiptId the receipt-id from the client frame
     * @return serialized RECEIPT frame
     */
    public static String receipt(String receiptId) {
        return buildFrame("RECEIPT", Map.of("receipt-id", receiptId), "");
    }

    /**
     * Builds a heartbeat frame (single newline as per STOMP specification).
     *
     * @return heartbeat string
     */
    public static String heartbeat() {
        return "\n";
    }

    // ─── Private builder ─────────────────────────────────────────────────────

    private static String buildFrame(String command, Map<String, String> headers, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append(command).append(LF);
        headers.forEach((k, v) -> sb.append(k).append(':').append(v).append(LF));
        sb.append(LF);
        if (body != null && !body.isEmpty()) {
            sb.append(body);
        }
        sb.append(NULL_BYTE);
        return sb.toString();
    }
}
