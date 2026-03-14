package org.mcbst.msmc;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.mcbst.msmc.client.match.ServerMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Client entrypoint: performs handshake with the server plugin and triggers latency matching.
 */
public class MsmcClient implements ClientModInitializer {
    public static final ResourceLocation HANDSHAKE = ResourceLocation.fromNamespaceAndPath(Msmc.MOD_ID, "handshake");
    public static final ResourceLocation ADDRESSES = ResourceLocation.fromNamespaceAndPath(Msmc.MOD_ID, "addresses");

    @Override
    public void onInitializeClient() {
        // Listen for address list pushed by the server plugin.
        ClientPlayNetworking.registerGlobalReceiver(ADDRESSES, (client, handler, buf, responseSender) -> {
            int count = buf.readVarInt();
            List<String> targets = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                targets.add(buf.readUtf());
            }
            client.execute(() -> {
                Msmc.LOGGER.info("Received {} target(s) from server for matching: {}", targets.size(), targets);
                ServerMatcher.startMatching(client, targets);
            });
        });

        // Send a minimal ping (zip.txt) once the connection is established so the plugin can respond.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            FriendlyByteBuf handshake = PacketByteBufs.create();
            handshake.writeUtf("zip.txt"); // Required marker so the plugin knows Msmc is installed.
            handshake.writeUtf(client.getGame().getVersion().getName()); // attach client version for logging
            sender.sendPacket(HANDSHAKE, handshake);
            Msmc.LOGGER.info("Sent Msmc handshake (zip.txt) to server.");
        });
    }
}
