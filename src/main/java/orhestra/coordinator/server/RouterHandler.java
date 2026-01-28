package orhestra.coordinator.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import orhestra.coordinator.api.Controller;
import orhestra.coordinator.api.Controller.ControllerResponse;
import orhestra.coordinator.config.CoordinatorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Central router that dispatches HTTP requests to registered controllers.
 * 
 * Only handles versioned API endpoints:
 * - /api/v1/* (public API)
 * - /internal/v1/* (internal agent API)
 * 
 * All other endpoints return 404.
 */
public class RouterHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(RouterHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .findAndRegisterModules();

    private final List<Controller> controllers = new ArrayList<>();
    private final CoordinatorConfig config;

    public RouterHandler(CoordinatorConfig config) {
        this.config = config;
    }

    /**
     * Register a controller to handle requests.
     * Controllers are checked in order of registration.
     */
    public RouterHandler registerController(Controller controller) {
        controllers.add(controller);
        log.debug("Registered controller: {}", controller.getClass().getSimpleName());
        return this;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        String uri = req.uri();
        HttpMethod method = req.method();

        // Extract path without query string
        String path = uri.contains("?") ? uri.substring(0, uri.indexOf("?")) : uri;

        // Check auth for internal endpoints
        if (!checkAuth(req, path)) {
            log.warn("Auth failed for {} {}", method, path);
            write(ctx, FORBIDDEN, "application/json", "{\"error\":\"forbidden\"}");
            return;
        }

        try {
            // Try registered controllers
            for (Controller controller : controllers) {
                if (controller.matches(method, path)) {
                    ControllerResponse response = controller.handle(ctx, req, path);
                    write(ctx, response.status(), response.contentType(), response.body());
                    return;
                }
            }

            // No controller matched - return 404
            log.debug("No handler for: {} {}", method, path);
            write(ctx, NOT_FOUND, "application/json", "{\"error\":\"not found\"}");

        } catch (IllegalArgumentException e) {
            // Validation errors
            log.warn("Validation error: {}", e.getMessage());
            write(ctx, BAD_REQUEST, "application/json",
                    "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        } catch (Exception e) {
            // Unexpected errors
            log.error("Handler error: {} {}", method, path, e);
            write(ctx, INTERNAL_SERVER_ERROR, "application/json", "{\"error\":\"internal error\"}");
        }
    }

    /**
     * Check if request requires and passes auth.
     */
    private boolean checkAuth(FullHttpRequest req, String path) {
        if (!config.hasAgentKey()) {
            return true; // No auth configured
        }

        // Only internal endpoints require auth
        if (!path.startsWith("/internal/")) {
            return true;
        }

        String providedKey = req.headers().get("X-Orhestra-Key");
        return config.agentKey().equals(providedKey);
    }

    private void write(ChannelHandlerContext ctx, HttpResponseStatus status, String contentType, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(CONTENT_TYPE, contentType + "; charset=utf-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        ctx.writeAndFlush(response);
    }

    private static String escapeJson(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Get the shared ObjectMapper for JSON serialization.
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
