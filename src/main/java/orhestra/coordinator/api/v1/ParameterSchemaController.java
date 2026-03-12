package orhestra.coordinator.api.v1;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import orhestra.coordinator.api.Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Controller that serves the parameter schema definition.
 *
 * GET /api/v1/parameter-schema
 *
 * Returns the static parameters.json from the classpath.
 * Plugin UIs use this response to auto-render parameter forms.
 */
public class ParameterSchemaController implements Controller {

    private static final Logger log = LoggerFactory.getLogger(ParameterSchemaController.class);
    private static final String SCHEMA_PATH = "/parameters.json";

    private final String schemaJson;

    public ParameterSchemaController() {
        this.schemaJson = loadSchema();
    }

    @Override
    public boolean matches(HttpMethod method, String path) {
        return method.equals(HttpMethod.GET) && "/api/v1/parameter-schema".equals(path);
    }

    @Override
    public ControllerResponse handle(ChannelHandlerContext ctx, FullHttpRequest req, String path) {
        return ControllerResponse.json(schemaJson);
    }

    private static String loadSchema() {
        try (InputStream is = ParameterSchemaController.class.getResourceAsStream(SCHEMA_PATH)) {
            if (is == null) {
                log.error("parameters.json not found on classpath at {}", SCHEMA_PATH);
                return "{\"error\":\"schema not found\"}";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to load parameters.json", e);
            return "{\"error\":\"failed to load schema\"}";
        }
    }
}
