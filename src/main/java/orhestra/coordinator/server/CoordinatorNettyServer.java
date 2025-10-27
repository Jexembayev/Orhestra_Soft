package orhestra.coordinator.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelMatchers;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import orhestra.coordinator.service.SpotRegistry;
import orhestra.coordinator.store.Db;
import orhestra.coordinator.store.dao.TaskDao;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

public final class CoordinatorNettyServer {

    /** Реестр спотов в памяти — UI читает отсюда */
    public static final SpotRegistry REGISTRY = new SpotRegistry();
    /** Простой генератор идентификаторов спотов (для /internal/hello) */
    public static final java.util.concurrent.atomic.AtomicInteger ID_GEN = new java.util.concurrent.atomic.AtomicInteger(1);

    /** Единые инстансы БД и DAO */
    public static final Db DB = new Db("jdbc:h2:mem:orhestra;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_UPPER=FALSE");
    public static final TaskDao TASKS = new TaskDao(DB);

    private static volatile boolean running = false;
    private static Channel serverChannel;
    private static EventLoopGroup bossGroup;
    private static EventLoopGroup workerGroup;

    static final ChannelGroup CLIENTS = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    // ---- агрегаторы логов ----
    static final LongAdder HB_COUNT = new LongAdder();
    static final LongAdder GET_TASK_COUNT = new LongAdder();

    private static volatile Consumer<String> logger = null;
    public static void setLogger(Consumer<String> l) { logger = l; }
    static void log(String s) { var l = logger; if (l != null) l.accept(s + "\n"); }

    private CoordinatorNettyServer() {}

    /** HTTP pipeline под агент */
    public static ChannelHandler pipelineInitializer(Consumer<String> logSink) {
        setLogger(logSink);
        return new ChannelInitializer<SocketChannel>() {
            @Override protected void initChannel(SocketChannel ch) {
                ChannelPipeline p = ch.pipeline();
                p.addLast(new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS));
                p.addLast(new HttpServerCodec());
                p.addLast(new HttpObjectAggregator(1 * 1024 * 1024));
                p.addLast(new CoordinatorHandler()); // REST
            }
        };
    }

    public static synchronized boolean start(int port) {
        if (running) return true;
        try {
            bossGroup   = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();

            ServerBootstrap b = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(pipelineInitializer(logger));

            serverChannel = b.bind(port).syncUninterruptibly().channel();
            running = true;
            log("Coordinator started on port " + port);

            // Периодическая сводка раз в секунду
            workerGroup.next().scheduleAtFixedRate(() -> {
                int spots = REGISTRY.size();
                long hb = HB_COUNT.sumThenReset();
                long gt = GET_TASK_COUNT.sumThenReset();
                if (hb > 0 || gt > 0) {
                    log("stats: hb/s=" + hb + " get-task/s=" + gt + " spots=" + spots);
                }
                // здесь можно добавить TTL-чистку спотов и освобождение задач (если реализуешь в SpotRegistry)
            }, 1, 1, TimeUnit.SECONDS);

            return true;
        } catch (Throwable t) {
            log("Start error: " + t.getMessage());
            stop();
            return false;
        }
    }

    public static synchronized void stop() {
        if (!running) return;
        try {
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
                serverChannel = null;
            }
            CLIENTS.close().awaitUninterruptibly();
        } finally {
            if (workerGroup != null) { workerGroup.shutdownGracefully(); workerGroup = null; }
            if (bossGroup != null)   { bossGroup.shutdownGracefully();   bossGroup = null;   }
            running = false;
            log("Coordinator stopped");
        }
    }

    public static boolean isRunning() { return running; }

    public static void broadcast(String line) {
        if (!running) return;
        CLIENTS.writeAndFlush(Objects.requireNonNull(line) + "\n", ChannelMatchers.isNot(serverChannel), true);
    }
}











