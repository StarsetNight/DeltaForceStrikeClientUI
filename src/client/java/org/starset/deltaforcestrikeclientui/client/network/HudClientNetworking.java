package org.starset.deltaforcestrikeclientui.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.starset.deltaforcestrikeclientui.client.hud.ClientHudState;
import org.starset.deltaforcestrikeclientui.client.music.MusicBox;
import org.starset.deltaforcestrikeclientui.network.DfsHudPayload;

public final class HudClientNetworking {

    private HudClientNetworking() {
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(DfsHudPayload.TYPE, DfsHudPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(DfsHudPayload.TYPE, (payload, context) -> {
            ClientHudState.apply(payload.snapshot());
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientHudState.clear();
            MusicBox.get().stopAll();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientHudState.clear();
            MusicBox.get().stopAll();
        });
    }
}
