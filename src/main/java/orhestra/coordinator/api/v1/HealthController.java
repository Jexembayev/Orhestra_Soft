package orhestra.coordinator.api.v1;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import orhestra.coordinator.api.Controller;
import orhestra.coordinator.api.v1.dto.HealthResponse;
import orhestra.coordinator.server.RouterHandler;
import orhestra.coordinator.service.SpotService;
import orhestra.coordinator.service.TaskService;
import orhestra.coordinator.store.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.time.Duration;

/**
 * Health check controller.
 * GET /api/v1/health
 */
public class HealthController implements Controller {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    private static final String VERSION = "2.0.0";

    private final Database database;
    private final SpotService spotService;
    private final TaskService taskService;

    public HealthController(Database database, SpotService spotService, TaskService taskService) {
        this.database = database;
        this.spotService = spotService;
        this.taskService = taskService;
    }

    @Override
    public boolean matches(HttpMethod method, String path) {
        return method.equals(HttpMethod.GET) && "/api/v1/health".equals(path);
    }

    @Override
    public ControllerResponse handle(ChannelHandlerContext ctx, FullHttpRequest req, String path) {
        try {
            // Check database connectivity
            boolean dbOk = database.isHealthy();

            if (!dbOk) {
                HealthResponse response = HealthResponse.unhealthy("connection failed");
                return ControllerResponse.json(
                        io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE,
                        RouterHandler.mapper().writeValueAsString(response));
            }

            // Gather stats
            String uptime = formatUptime();
            int activeSpots = spotService.countActive();
            int pendingTasks = taskService.countPending();
            int runningTasks = taskService.countRunning();

            HealthResponse response = HealthResponse.healthy(
                    uptime, VERSION, activeSpots, pendingTasks, runningTasks);

            return ControllerResponse.json(RouterHandler.mapper().writeValueAsString(response));

        } catch (Exception e) {
            log.error("Health check failed", e);
            try {
                HealthResponse response = HealthResponse.unhealthy(e.getMessage());
                return ControllerResponse.json(
                        io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE,
                        RouterHandler.mapper().writeValueAsString(response));
            } catch (Exception ex) {
                return ControllerResponse.error("health check failed");
            }
        }
    }

    private String formatUptime() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        Duration duration = Duration.ofMillis(uptimeMs);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        return hours + "h " + minutes + "m";
    }
}
