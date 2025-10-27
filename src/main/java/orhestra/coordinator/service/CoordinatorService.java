package orhestra.coordinator.service;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import orhestra.coordinator.server.CoordinatorNettyServer;
import orhestra.coordinator.store.model.SpotNode;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Обёртка над Netty-сервером координатора.
 * - setLogger / setLogSink: лог в UI
 * - setSpotListener: стримим снапшоты в SpotMonitoring
 * - start/stop/isRunning
 */
public class CoordinatorService {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel serverChannel;

    private Consumer<String> logSink = s -> {}; // по умолчанию молчим
    /** теперь SpotNode, а не SpotRow */
    private Consumer<List<SpotNode>> spotListener = null;

    private ScheduledExecutorService ticker;

    /** Совместимость с CloudController. */
    public void setLogger(Consumer<String> sink) { setLogSink(sink); }

    public void setLogSink(Consumer<String> sink) {
        this.logSink = (sink != null) ? sink : s -> {};
        CoordinatorNettyServer.setLogger(this.logSink);
    }

    /** UI подпишется и будет получать раз в секунду снапшоты SpotNode. */
    public void setSpotListener(Consumer<List<SpotNode>> listener) {
        this.spotListener = listener;
    }

    public boolean isRunning() { return running.get(); }

    public boolean start(int port) {
        if (running.get()) {
            logSink.accept("Coordinator already running");
            return true;
        }
        try {
            boss   = new NioEventLoopGroup(1);
            worker = new NioEventLoopGroup();

            ServerBootstrap b = new ServerBootstrap()
                    .group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(CoordinatorNettyServer.pipelineInitializer(logSink));

            serverChannel = b.bind(port).syncUninterruptibly().channel();
            serverChannel.closeFuture().addListener(f -> stop());

            // тикер снапшотов для SpotMonitoring (SpotNode)
            ticker = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "coord-snapshot");
                t.setDaemon(true);
                return t;
            });
            ticker.scheduleAtFixedRate(() -> {
                var l = spotListener;
                if (l != null) {
                    // <<< ВАЖНО: используем новый API реестра
                    List<SpotNode> snapshot = CoordinatorNettyServer.REGISTRY.snapshotNodes();
                    l.accept(snapshot);
                }
            }, 1000, 1000, TimeUnit.MILLISECONDS);

            running.set(true);
            logSink.accept("Coordinator started on port " + port);
            return true;
        } catch (Throwable t) {
            logSink.accept("Coordinator start error: " + t.getMessage());
            stop();
            return false;
        }
    }

    public void stop() {
        if (!running.get()) return;
        try {
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
                serverChannel = null;
            }
        } finally {
            if (ticker != null) { ticker.shutdownNow(); ticker = null; }
            if (worker != null) { worker.shutdownGracefully(); worker = null; }
            if (boss != null)   { boss.shutdownGracefully();   boss = null;   }
            running.set(false);
            logSink.accept("Coordinator stopped");
        }
    }
}


