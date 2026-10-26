package org.starset.dfsui.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import org.starset.dfsui.client.command.DfscuiCommand;
import org.starset.dfsui.client.config.ClientUiConfig;
import org.starset.dfsui.client.hud.Cs2HudOverlay;
import org.starset.dfsui.client.music.MediaMusicPlayer;
import org.starset.dfsui.client.music.MusicBox;
import org.starset.dfsui.client.network.HudClientNetworking;
import org.starset.dfsui.client.network.TournamentHandshakeClient;
import org.starset.dfsui.common.ModInfo;

public class DeltaForceStrikeClientUIClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientUiConfig.loadOrCreate();
        HudClientNetworking.init();
        DfscuiCommand.register();
        TournamentHandshakeClient.init();
        // 后台预热 ffplay 探测，避免首次播放在主线程探测卡顿
        MediaMusicPlayer.warmupFfplayLookup();

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(ModInfo.MOD_ID, "cs2_hud"),
                new Cs2HudOverlay()
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> MusicBox.get().tick());
    }
}
