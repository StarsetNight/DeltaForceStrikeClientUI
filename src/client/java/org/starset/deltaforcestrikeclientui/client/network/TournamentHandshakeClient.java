package org.starset.deltaforcestrikeclientui.client.network;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.starset.deltaforcestrikeclientui.network.TournamentHandshakePayload;
import org.starset.deltaforcestrikeclientui.network.TournamentHandshakeResponsePayload;

import java.nio.charset.StandardCharsets;

/**
 * 赛事握手客户端网络处理器：
 * <ul>
 *   <li>注册 server-bound / client-bound payload 类型</li>
 *   <li>连接进入游戏后自动发送握手，直到收到 DFS_OK 或最多 3 秒重试</li>
 *   <li>DFS_OK 通过 / DFS_FAIL <reason> 失败（仅 toast/console 日志）</li>
 * </ul>
 *
 * <p>重试机制的原因：ClientPlayConnectionEvents.JOIN 触发时
 * 服务端可能尚未完成对该玩家的 channel 同步；发送的握手包可能被丢弃。
 * 改为每秒重试一次直到服务端 OK，或达到 3 秒上限。</p>
 */
public final class TournamentHandshakeClient {

    public static final String MOD_ID = "deltaforcestrikeclientui";
    public static final String MOD_VERSION = "1.1.0";

    /** 是否已收到 DFS_OK */
    private static boolean verified = false;
    /** 已重试次数（每秒一次；超过 MAX 后停止） */
    private static int attempts = 0;
    /** tick 计数器，每 20 tick 发一次 */
    private static int tickCount = 0;
    private static final int MAX_ATTEMPTS = 4;

    private TournamentHandshakeClient() {
    }

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(
                TournamentHandshakePayload.TYPE,
                TournamentHandshakePayload.CODEC);

        PayloadTypeRegistry.clientboundPlay().register(
                TournamentHandshakeResponsePayload.TYPE,
                TournamentHandshakeResponsePayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(
                TournamentHandshakeResponsePayload.TYPE,
                (payload, context) -> {
                    String text = payload.text() == null ? "" : payload.text();
                    context.client().execute(() -> {
                        if (text.startsWith("DFS_OK")) {
                            verified = true;
                        } else if (text.startsWith("DFS_FAIL")) {
                            String reason = text.substring("DFS_FAIL".length()).trim();
                            var player = net.minecraft.client.Minecraft.getInstance().player;
                            if (player != null) {
                                player.connection.sendChat(
                                        "§e[DFS] 赛事握手失败: §c" + reason);
                            }
                        }
                    });
                });

        // 进入服务器：初始化标记
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            verified = false;
            attempts = 0;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            verified = false;
            attempts = 0;
        });

        // 每 20 ticks 检查一次：未验证且未达上限则重发握手
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getConnection() == null) return;
            if (verified || attempts >= MAX_ATTEMPTS) return;
            // 每 20 tick（约 1s）一次
            tickCount++;
            if (tickCount < 20) return;
            tickCount = 0;
            attempts++;
            try {
                String text = "DFS_HANDSHAKE 1 " + MOD_VERSION + " " + MOD_ID;
                ClientPlayNetworking.send(new TournamentHandshakePayload(text));
            } catch (Throwable ignored) {
                // 通道可能尚未 ready，下个 tick 再试
            }
        });
    }
}
