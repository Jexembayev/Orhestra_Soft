package orhestra.coordinator.api;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Base interface for HTTP controllers.
 * Controllers handle specific URL patterns and HTTP methods.
 */
public interface Controller {

    /**
     * Check if this controller can handle the given request.
     *
     * @param method HTTP method
     * @param path   Request path (without query string)
     * @return true if this controller handles this request
     */
    boolean matches(HttpMethod method, String path);

    /**
     * Handle the request.
     *
     * @param ctx  Netty channel context
     * @param req  Full HTTP request
     * @param path Request path (without query string)
     * @return Response to send back
     */
    ControllerResponse handle(ChannelHandlerContext ctx, FullHttpRequest req, String path);

    /**
     * Response from a controller.
     */
    record ControllerResponse(
            HttpResponseStatus status,
            String contentType,
            String body) {

        public static ControllerResponse json(String body) {
            return new ControllerResponse(HttpResponseStatus.OK, "application/json", body);
        }

        public static ControllerResponse json(HttpResponseStatus status, String body) {
            return new ControllerResponse(status, "application/json", body);
        }

        public static ControllerResponse text(String body) {
            return new ControllerResponse(HttpResponseStatus.OK, "text/plain", body);
        }

        public static ControllerResponse text(HttpResponseStatus status, String body) {
            return new ControllerResponse(status, "text/plain", body);
        }

        public static ControllerResponse notFound(String message) {
            return new ControllerResponse(HttpResponseStatus.NOT_FOUND, "text/plain", message);
        }

        public static ControllerResponse badRequest(String message) {
            return new ControllerResponse(HttpResponseStatus.BAD_REQUEST, "application/json",
                    "{\"error\":\"" + escapeJson(message) + "\"}");
        }

        public static ControllerResponse error(String message) {
            return new ControllerResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, "application/json",
                    "{\"error\":\"" + escapeJson(message) + "\"}");
        }

        public static ControllerResponse forbidden(String message) {
            return new ControllerResponse(HttpResponseStatus.FORBIDDEN, "text/plain", message);
        }

        public static ControllerResponse conflict(String message) {
            return new ControllerResponse(HttpResponseStatus.CONFLICT, "application/json",
                    "{\"error\":\"" + escapeJson(message) + "\"}");
        }

        private static String escapeJson(String s) {
            if (s == null)
                return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
