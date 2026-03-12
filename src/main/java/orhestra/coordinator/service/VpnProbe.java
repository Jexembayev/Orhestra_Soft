package orhestra.coordinator.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Проверяет состояние OpenVPN и определяет IP-адрес tun-интерфейса.
 */
public class VpnProbe {

    public boolean isVpnUp() {
        return getVpnIp().isPresent();
    }

    /**
     * Возвращает IPv4-адрес первого tun-интерфейса OpenVPN.
     * Работает на macOS и Linux.
     *
     * @return Optional с IP-адресом, либо empty если VPN не поднят
     */
    public Optional<String> getVpnIp() {
        try {
            boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
            if (win) {
                // На Windows ищем в ipconfig адрес TAP/TUN адаптера
                return parseWindowsVpnIp(exec("ipconfig", "/all"));
            } else {
                // macOS: ifconfig, Linux: ip addr
                String ifconfig = exec("ifconfig");
                if (!ifconfig.isBlank()) {
                    Optional<String> ip = parseTunIp(ifconfig, true);
                    if (ip.isPresent())
                        return ip;
                }
                return parseTunIp(exec("ip", "addr"), false);
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Парсит вывод ifconfig (macOS/Linux) и ищет IPv4 адрес на tun* интерфейсе.
     *
     * @param output  вывод ifconfig / ip addr
     * @param isMacOs true — формат macOS (ifconfig), false — формат Linux (ip addr)
     */
    private static Optional<String> parseTunIp(String output, boolean isMacOs) {
        // Разбиваем на блоки по интерфейсам
        // macOS: "tun0: flags=..."
        // Linux: "5: tun0: <..."
        String[] lines = output.split("\n");

        boolean inTun = false;
        for (String line : lines) {
            String trimmed = line.trim();

            // Начало нового интерфейса
            if (!line.startsWith(" ") && !line.startsWith("\t")) {
                // macOS: "utun0: ..." или "tun0: ..."
                // Linux: "5: tun0: <..."
                inTun = trimmed.toLowerCase().matches("(\\d+:\\s+)?u?tun\\d+.*");
            }

            if (inTun) {
                // Ищем строку вида "inet 10.x.x.x ..." или "inet addr:10.x.x.x"
                Matcher m = Pattern.compile("inet\\s+(addr:)?(\\d+\\.\\d+\\.\\d+\\.\\d+)").matcher(trimmed);
                if (m.find()) {
                    String ip = m.group(2);
                    // Исключаем loopback
                    if (!ip.startsWith("127.")) {
                        return Optional.of(ip);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Парсит вывод ipconfig /all на Windows, ищет TAP/TUN адаптер. */
    private static Optional<String> parseWindowsVpnIp(String output) {
        boolean inTap = false;
        for (String line : output.split("\n")) {
            String lower = line.toLowerCase();
            if (lower.contains("tap") || lower.contains("tun") || lower.contains("openvpn")) {
                inTap = true;
            }
            if (inTap && lower.contains("ipv4") && lower.contains(":")) {
                Matcher m = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)").matcher(line);
                if (m.find()) {
                    String ip = m.group(1);
                    if (!ip.startsWith("127."))
                        return Optional.of(ip);
                }
            }
            // Новый адаптер сбрасывает состояние
            if (!line.startsWith(" ") && !line.startsWith("\t") && !line.isBlank()) {
                if (!lower.contains("tap") && !lower.contains("tun") && !lower.contains("openvpn")) {
                    inTap = false;
                }
            }
        }
        return Optional.empty();
    }

    private static String exec(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        byte[] out = p.getInputStream().readAllBytes();
        p.waitFor();
        return new String(out, StandardCharsets.UTF_8);
    }
}
