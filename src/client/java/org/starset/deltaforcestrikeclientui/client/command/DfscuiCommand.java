package org.starset.deltaforcestrikeclientui.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import org.starset.deltaforcestrikeclientui.client.config.ClientUiConfig;
import org.starset.deltaforcestrikeclientui.client.music.MusicBox;

public final class DfscuiCommand {

    private DfscuiCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
                ClientCommands.literal("dfscui")
                        .then(ClientCommands.literal("reload")
                                .executes(DfscuiCommand::reload))
                        .then(ClientCommands.literal("stopmusic")
                                .executes(ctx -> {
                                    MusicBox.get().stopAll();
                                    ctx.getSource().sendFeedback(Component.literal("§a[DFS-CUI] 已停止音乐"));
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(Component.literal(
                                    "§e/dfscui reload §7— 重载配置\n" +
                                            "§e/dfscui stopmusic §7— 停止音乐\n" +
                                            "§7配置: " + ClientUiConfig.configPath()));
                            return 1;
                        })
        );
    }

    private static int reload(CommandContext<FabricClientCommandSource> ctx) {
        try {
            ClientUiConfig.reload();
            MusicBox.get().onConfigReload();
            ctx.getSource().sendFeedback(Component.literal(
                    "§a[DFS-CUI] 配置已重载: " + ClientUiConfig.configPath()));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Component.literal("§c[DFS-CUI] 重载失败: " + e.getMessage()));
            return 0;
        }
    }
}
