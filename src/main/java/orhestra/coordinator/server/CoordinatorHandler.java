package orhestra.coordinator.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/** REST-ручки для агента на споте. */
class CoordinatorHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final ObjectMapper M = new ObjectMapper();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        String uri = req.uri();
        HttpMethod m = req.method();
        CoordinatorNettyServer.log("HTTP " + m + " " + uri);

        try {
            if (m.equals(HttpMethod.GET) && uri.equals("/ping")) {
                write(ctx, OK, "text/plain", "PONG\n"); return;
            }

            if (m.equals(HttpMethod.POST) && uri.equals("/internal/heartbeat")) {
                String body = req.content().toString(StandardCharsets.UTF_8);
                Map<String, Object> hb = M.readValue(body, new TypeReference<>(){});

                String nodeId    = String.valueOf(hb.getOrDefault("nodeId", "unknown"));
                double cpu       = asDouble(hb.get("cpuLoad"), 0);
                int tasks        = (int) asDouble(hb.get("activeTasks"), 0);
                int totalCores   = (int) asDouble(hb.get("totalCores"), 0);

                String ip = ((InetSocketAddress) ctx.channel().remoteAddress())
                        .getAddress().getHostAddress();

                CoordinatorNettyServer.REGISTRY.heartbeat(nodeId, cpu, tasks, totalCores, ip);
                CoordinatorNettyServer.log("HB ok: nodeId=" + nodeId
                        + " cpu=" + cpu + " tasks=" + tasks + " cores=" + totalCores
                        + " size=" + CoordinatorNettyServer.REGISTRY.size());
                write(ctx, OK, "text/plain", "OK\n"); return;
            }

            if (m.equals(HttpMethod.GET) && uri.startsWith("/internal/get-task")) {
                write(ctx, NOT_FOUND, "text/plain", "no-task\n"); return;
            }

            if (m.equals(HttpMethod.POST) && uri.equals("/internal/task-result")) {
                String body = req.content().toString(StandardCharsets.UTF_8);
                CoordinatorNettyServer.log("RESULT: " + body);
                write(ctx, OK, "text/plain", "OK\n"); return;
            }

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








