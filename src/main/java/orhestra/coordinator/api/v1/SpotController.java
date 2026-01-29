package orhestra.coordinator.api.v1;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import orhestra.coordinator.api.Controller;
import orhestra.coordinator.api.v1.dto.SpotInfoResponse;
import orhestra.coordinator.model.Spot;
import orhestra.coordinator.server.RouterHandler;
import orhestra.coordinator.service.SpotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Controller for SPOT node public API.
 * GET /api/v1/spots - List all SPOTs
 * 
 * Exceptions bubble to RouterHandler for proper error responses.
 */
public class SpotController implements Controller {

    private static final Logger log = LoggerFactory.getLogger(SpotController.class);

    private final SpotService spotService;

    public SpotController(SpotService spotService) {
        this.spotService = spotService;
    }

    @Override
    public boolean matches(HttpMethod method, String path) {
        return method.equals(HttpMethod.GET) && "/api/v1/spots".equals(path);
    }

    @Override
    public ControllerResponse handle(ChannelHandlerContext ctx, FullHttpRequest req, String path) {
        // No try-catch hiding - let exceptions bubble to RouterHandler for proper error
        // details
        List<Spot> spots = spotService.findAll();

        List<SpotInfoResponse> spotResponses = spots.stream()
                .map(SpotInfoResponse::from)
                .toList();

        Map<String, Object> response = Map.of("spots", spotResponses);

        try {
            return ControllerResponse.json(RouterHandler.mapper().writeValueAsString(response));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Wrap in RuntimeException - RouterHandler will catch and expose details
            throw new RuntimeException("Failed to serialize spots response", e);
        }
    }
}
