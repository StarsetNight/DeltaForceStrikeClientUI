package org.starset.dfsui.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.starset.dfsui.client.hud.ClientHudState;
import org.starset.dfsui.client.music.MusicBox;
import org.starset.dfsui.network.DfsHudPayload;

public final class HudClientNetworking {

    private HudClientNetworking() {
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(DfsHudPayload.TYPE, DfsHudPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(DfsHudPayload.TYPE, (payload, context) -> {
            ClientHudState.apply(payload.snapshot());
        });

        // 进入 / 离开服务器时清除 HUD 快照并停止音乐，避免跨世界残留。
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
