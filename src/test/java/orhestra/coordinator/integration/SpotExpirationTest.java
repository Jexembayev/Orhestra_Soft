package orhestra.coordinator.integration;

import orhestra.coordinator.config.CoordinatorConfig;
import orhestra.coordinator.config.Dependencies;
import orhestra.coordinator.model.Spot;
import orhestra.coordinator.model.SpotStatus;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: spot expires and is deleted after heartbeat timeout.
 */
class SpotExpirationTest {

    private static Dependencies deps;

    @BeforeAll
    static void setUp() {
        CoordinatorConfig config = CoordinatorConfig.defaults()
                .withDatabaseUrl(
                        "jdbc:h2:mem:spotexpire-" + System.nanoTime() + ";MODE=PostgreSQL;DATABASE_TO_UPPER=FALSE");
        deps = Dependencies.create(config);
    }

    @AfterAll
    static void tearDown() {
        if (deps != null)
            deps.close();
    }

    @Test
    void spotRemovedAfterHeartbeatTimeout() throws InterruptedException {
        // Register a spot via heartbeat
        deps.spotService().heartbeat("test-spot-1", "127.0.0.1", 10.0, 0, 4, 0, 0);

        // Verify spot exists
        List<Spot> spots = deps.spotService().findAll();
        assertEquals(1, spots.size());
        assertEquals("test-spot-1", spots.get(0).id());
        assertEquals(SpotStatus.UP, spots.get(0).status());

        // Wait longer than heartbeat timeout (10s) - but we use the reaper cutoff
        // directly
        // Instead of waiting, call reaper which checks Instant.now() - 10s
        // The spot was just created, so it's within timeout. Wait a bit more.
        Thread.sleep(11_000);

        // Now reap — spot should be stale
        int reaped = deps.spotService().reapStaleSpots();
        assertEquals(1, reaped, "Should have reaped 1 stale spot");

        // Verify spot is deleted
        List<Spot> remaining = deps.spotService().findAll();
        assertTrue(remaining.isEmpty(), "Spot list should be empty after reaper ran");
    }

    @Test
    void spotNotRemovedWhileHeartbeating() {
        // Register a spot
        deps.spotService().heartbeat("test-spot-2", "127.0.0.1", 20.0, 1, 8, 4096, 16384);

        // Immediately run reaper (spot just sent heartbeat)
        int reaped = deps.spotService().reapStaleSpots();
        assertEquals(0, reaped, "Should not reap active spot");

        // Spot should still exist
        List<Spot> spots = deps.spotService().findAll();
        assertFalse(spots.isEmpty(), "Active spot should remain");

        // Cleanup
        deps.spotService().delete("test-spot-2");
    }
}
