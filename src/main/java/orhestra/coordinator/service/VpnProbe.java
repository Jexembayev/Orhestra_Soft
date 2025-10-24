package orhestra.coordinator.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class VpnProbe {
    public boolean isVpnUp() {
        try {
            boolean win = System.getProperty("os.name","").toLowerCase().contains("win");
            String out = win ? exec("ipconfig") : firstNonBlank(exec("ifconfig"), exec("ip","addr"));
            // подстрой маску под свою VPN-сеть
            return out.contains("10.") || out.toLowerCase().contains("tun") || out.toLowerCase().contains("tap");
        } catch (Exception e) {
            return false;
        }
    }

    private static String firstNonBlank(String... lines) {
        for (String s : lines) if (s != null && !s.isBlank()) return s;
        return "";
    }
    private static String exec(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        byte[] out = p.getInputStream().readAllBytes();
        p.waitFor();
        return new String(out, StandardCharsets.UTF_8);
    }
}

