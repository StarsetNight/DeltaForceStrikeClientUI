package org.starset.deltaforcestrikeclientui.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import org.starset.deltaforcestrikeclientui.client.command.DfscuiCommand;
import org.starset.deltaforcestrikeclientui.client.config.ClientUiConfig;
import org.starset.deltaforcestrikeclientui.client.hud.Cs2HudOverlay;
import org.starset.deltaforcestrikeclientui.client.music.MusicBox;
import org.starset.deltaforcestrikeclientui.client.network.HudClientNetworking;
import org.starset.deltaforcestrikeclientui.client.network.TournamentHandshakeClient;

public class DeltaforcestrikeclientuiClient implements ClientModInitializer {

    public static final String MOD_ID = "deltaforcestrikeclientui";

    @Override
    public void onInitializeClient() {
        ClientUiConfig.loadOrCreate();
        HudClientNetworking.init();
        DfscuiCommand.register();
        TournamentHandshakeClient.init();

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(MOD_ID, "cs2_hud"),
                new Cs2HudOverlay()
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> MusicBox.get().tick());
    }
}
