package orhestra;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import orhestra.coordinator.config.CoordinatorConfig;
import orhestra.coordinator.server.CoordinatorNettyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * JavaFX application entry point.
 * 
 * Starts the Coordinator server BEFORE loading the UI to ensure
 * dependencies are available when controllers initialize.
 */
public class App extends Application {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    @Override
    public void start(Stage stage) throws Exception {
        // Start coordinator server in background BEFORE loading UI
        CoordinatorConfig config = CoordinatorConfig.fromEnv();
        int port = config.serverPort();

        CompletableFuture<Boolean> serverStarted = CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Starting Coordinator server on port {}...", port);
                boolean started = CoordinatorNettyServer.start(port, config);
                if (started) {
                    log.info("Coordinator server started successfully");
                }
                return started;
            } catch (Exception e) {
                log.error("Failed to start Coordinator server", e);
                return false;
            }
        });

        // Wait for server to start (with short timeout for UI responsiveness)
        boolean started = false;
        try {
            started = serverStarted.get();
        } catch (Exception e) {
            log.error("Server startup failed", e);
        }

        if (!started) {
            log.warn("Server did not start - UI will show 'Server not started' message");
        }

        // Now load the FXML UI - controllers will use tryDependencies() safely
        FXMLLoader loader = new FXMLLoader(
                Objects.requireNonNull(getClass().getResource("/orhestra/ui/main.fxml")));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1200, 750);
        stage.setTitle("Orhestra • Control Panel");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        // Gracefully stop the coordinator server when UI closes
        log.info("Application closing, stopping server...");
        CoordinatorNettyServer.stop();
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
