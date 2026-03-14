package org.mcbst.msmc.client.match;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.mcbst.msmc.Msmc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Background helper that pings candidate endpoints and connects to the best one.
 */
public final class ServerMatcher {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "msmc-matcher");
        t.setDaemon(true);
        return t;
    });

    private ServerMatcher() {
    }

    public static void startMatching(Minecraft client, List<String> rawAddresses) {
        if (rawAddresses == null || rawAddresses.isEmpty()) {
            Msmc.LOGGER.warn("No addresses provided by server plugin; skipping matching.");
            return;
        }
        // Show full-screen message while we probe.
        Screen waiting = new WaitingScreen();
        client.setScreen(waiting);

        CompletableFuture
                .supplyAsync(() -> chooseBest(rawAddresses), EXECUTOR)
                .whenComplete((result, throwable) -> client.execute(() -> {
                    if (throwable != null) {
                        Msmc.LOGGER.error("Failed to match best server", throwable);
                        client.setScreen(null);
                        return;
                    }
                    if (result == null || result.loss >= 1.0) {
                        Msmc.LOGGER.warn("Could not reach any of the provided addresses: {}", rawAddresses);
                        client.setScreen(null);
                        return;
                    }

                    Msmc.LOGGER.info("Selected {} (avg {} ms, loss {}%)",
                            result.address, result.avgLatencyMs, Math.round(result.loss * 100));

                    ServerAddress target = ServerAddress.parseString(result.address);
                    ServerData data = new ServerData("Msmc 匹配", result.address, false);
                    // ConnectScreen handles disconnecting from the current server before opening a new connection.
                    ConnectScreen.startConnecting(waiting, client, target, data, false, null);
                }));
    }

    private static Result chooseBest(List<String> addresses) {
        return addresses.stream()
                .map(ServerMatcher::probe)
                .filter(Objects::nonNull)
                .min(Comparator
                        .comparingDouble((Result r) -> r.loss)
                        .thenComparingLong(r -> r.avgLatencyMs))
                .orElse(null);
    }

    private static Result probe(String address) {
        String host = address;
        int port = 25565;
        if (address.contains(":")) {
            String[] parts = address.split(":", 2);
            host = parts[0];
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }

        int attempts = 5;
        int successes = 0;
        long totalLatency = 0;
        int timeoutMs = (int) Duration.ofSeconds(1).toMillis();

        for (int i = 0; i < attempts; i++) {
            long start = System.nanoTime();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), timeoutMs);
                long latency = (System.nanoTime() - start) / 1_000_000L;
                successes++;
                totalLatency += latency;
            } catch (IOException ignored) {
                // count as loss
            }
        }

        double loss = 1.0 - (successes / (double) attempts);
        long avgLatency = successes == 0 ? Long.MAX_VALUE : totalLatency / successes;
        return new Result(address, avgLatency, loss);
    }

    private record Result(String address, long avgLatencyMs, double loss) {
    }
}
