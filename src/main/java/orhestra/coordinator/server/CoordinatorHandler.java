package orhestra.coordinator.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.QueryStringDecoder;
import orhestra.coordinator.core.AppBus;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

class CoordinatorHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final ObjectMapper M = new ObjectMapper();
    /** Чтобы один раз отметить источник поллинга get-task */
    private static final Set<String> POLLERS = ConcurrentHashMap.newKeySet();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        String uri = req.uri();
        HttpMethod m = req.method();

        // Мини-авторизация по заголовку (опционально). Если свойство не задано — ключ не проверяем.
        String requireKey = System.getProperty("ORHESTRA_AGENT_KEY"); // например, -DORHESTRA_AGENT_KEY=dev
        String hdrKey = req.headers().get("X-Orhestra-Key");
        boolean needsKey =
                (m.equals(HttpMethod.POST) && "/internal/hello".equals(uri)) ||
                        (m.equals(HttpMethod.POST) && "/internal/heartbeat".equals(uri)) ||
                        (m.equals(HttpMethod.GET)  && uri.startsWith("/internal/get-task")) ||
                        (m.equals(HttpMethod.POST) && "/internal/task-result".equals(uri));
        if (requireKey != null && needsKey && !requireKey.equals(hdrKey)) {
            CoordinatorNettyServer.log("DENY " + m + " " + uri + " UA=" + req.headers().get("User-Agent"));
            write(ctx, FORBIDDEN, "text/plain", "forbidden\n"); return;
        }

        // Логируем только неожиданные урлы
        boolean known = (m.equals(HttpMethod.POST) && "/internal/hello".equals(uri))
                || (m.equals(HttpMethod.POST) && "/internal/heartbeat".equals(uri))
                || (m.equals(HttpMethod.GET)  && uri.startsWith("/internal/get-task"))
                || (m.equals(HttpMethod.POST) && "/internal/task-result".equals(uri))
                || (m.equals(HttpMethod.GET)  && "/ping".equals(uri));
        if (!known) CoordinatorNettyServer.log("HTTP " + m + " " + uri);

        try {
            // ---------- ping ----------
            if (m.equals(HttpMethod.GET) && "/ping".equals(uri)) {
                write(ctx, OK, "text/plain", "PONG\n"); return;
            }

            // ---------- выдача уникального nodeId ----------
            if (m.equals(HttpMethod.POST) && "/internal/hello".equals(uri)) {
                int id = CoordinatorNettyServer.ID_GEN.getAndIncrement();
                String ip = ((InetSocketAddress) ctx.channel().remoteAddress())
                        .getAddress().getHostAddress();
                String json = M.writeValueAsString(Map.of("nodeId", String.valueOf(id), "ip", ip));
                write(ctx, OK, "application/json", json); return;
            }

            // ---------- heartbeat ----------
            if (m.equals(HttpMethod.POST) && "/internal/heartbeat".equals(uri)) {
                String body = req.content().toString(StandardCharsets.UTF_8);
                Map<String, Object> hb = M.readValue(body, new TypeReference<>(){});

                String nodeId    = String.valueOf(hb.getOrDefault("nodeId", "unknown"));
                double cpu       = asDouble(hb.get("cpuLoad"), 0);
                int tasks        = (int) asDouble(hb.get("activeTasks"), 0);
                int totalCores   = (int) asDouble(hb.get("totalCores"), 0);

                String ip = ((InetSocketAddress) ctx.channel().remoteAddress())
                        .getAddress().getHostAddress();

                int before = CoordinatorNettyServer.REGISTRY.size();
                CoordinatorNettyServer.REGISTRY.heartbeat(nodeId, cpu, tasks, totalCores, ip);
                int after = CoordinatorNettyServer.REGISTRY.size();

                CoordinatorNettyServer.HB_COUNT.increment();

                if (after > before) {
                    CoordinatorNettyServer.log("spot up: " + nodeId + " ip=" + ip + " cores=" + totalCores);
                }

                AppBus.fireSpotsChanged();
                write(ctx, OK, "text/plain", "OK\n"); return;
            }

            // ---------- get-task (ОДНА задача, как ждёт агент) ----------
            if (m.equals(HttpMethod.GET) && uri.startsWith("/internal/get-task")) {
                String ip = ((InetSocketAddress) ctx.channel().remoteAddress())
                        .getAddress().getHostAddress();
                if (POLLERS.add(ip)) CoordinatorNettyServer.log("get-task poller: " + ip);

                QueryStringDecoder q = new QueryStringDecoder(uri);
                String nodeId = q.parameters().getOrDefault("nodeId", java.util.List.of("unknown")).get(0);

                Optional<orhestra.coordinator.store.dao.TaskDao.TaskPick> pick =
                        CoordinatorNettyServer.TASKS.pickOneAndAssign(nodeId);

                CoordinatorNettyServer.GET_TASK_COUNT.increment();

                if (pick.isEmpty()) { write(ctx, NOT_FOUND, "text/plain", "no-task\n"); return; }

                AppBus.fireTasksChanged();

                String json = M.writeValueAsString(Map.of(
                        "id", pick.get().id(),
                        "payload", M.readTree(pick.get().payload())
                ));
                write(ctx, OK, "application/json", json); return;
            }

            // ---------- task-result (поддержка taskId/ok/status) ----------
            if (m.equals(HttpMethod.POST) && "/internal/task-result".equals(uri)) {
                String body = req.content().toString(StandardCharsets.UTF_8);
                var node = M.readTree(body);

                // id может приходить как "id" или "taskId"
                String id = node.path("id").asText(null);
                if (id == null || id.isBlank()) id = node.path("taskId").asText();

                // ok может быть boolean ok, либо строковый status == "OK"
                boolean ok = node.has("ok")
                        ? node.path("ok").asBoolean(false)
                        : "OK".equalsIgnoreCase(node.path("status").asText());

                if (ok) {
                    Long runtimeMs = node.has("runtimeMs") ? node.path("runtimeMs").asLong() : 0L;
                    Integer iter   = node.has("iter")      ? node.path("iter").asInt()      : null;
                    Double fopt    = node.has("fopt")      ? node.path("fopt").asDouble()   : null;
                    // можешь складывать любые сырые массивы/json, например bestPos → charts
                    String charts  = node.has("charts")
                            ? node.path("charts").toString()
                            : (node.has("bestPos") ? node.path("bestPos").toString() : null);

                    CoordinatorNettyServer.TASKS.completeOkWithMetrics(id, runtimeMs, iter, fopt, charts);
                } else {
                    CoordinatorNettyServer.TASKS.completeFailed(id);
                }

                CoordinatorNettyServer.log("task-result: " + id + " ok=" + ok);
                AppBus.fireTasksChanged();
                write(ctx, OK, "text/plain", "OK\n"); return;
            }

            // ---------- unknown ----------
            CoordinatorNettyServer.log("HTTP 404 " + uri);
            write(ctx, NOT_FOUND, "text/plain", "unknown\n");
        } catch (Exception e) {
            CoordinatorNettyServer.log("HTTP handler error: " + e.getMessage());
            write(ctx, INTERNAL_SERVER_ERROR, "text/plain", "ERR\n");
        }
    }

    private static void write(ChannelHandlerContext ctx, HttpResponseStatus st, String ct, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, st, Unpooled.wrappedBuffer(b));
        resp.headers().set(CONTENT_TYPE, ct + "; charset=utf-8");
        resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, b.length);
        ctx.writeAndFlush(resp);
    }

    private static double asDouble(Object v, double def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception ignore) { return def; }
    }
}













