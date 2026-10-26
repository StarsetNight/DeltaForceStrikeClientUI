package org.starset.dfsui.client.network;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import org.starset.dfsui.common.ModInfo;
import org.starset.dfsui.network.TournamentTextPayload;

/**
 * 赛事握手客户端网络处理器：
 * <ul>
 *   <li>在 server-bound / client-bound 两个方向注册同一条 {@code deltaforcestrike:tournament}
 *       通道的文本 payload 类型</li>
 *   <li>进入游戏后自动发送握手，直到收到 DFS_OK 或最多 3 秒重试</li>
 *   <li>DFS_OK 通过 / DFS_FAIL &lt;reason&gt; 失败（仅聊天/控制台提示）</li>
 * </ul>
 *
 * <p>重试机制的原因：{@code ClientPlayConnectionEvents.JOIN} 触发时服务端可能尚未完成
 * 对该玩家的 channel 同步，握手包可能被丢弃，因此每秒重试直到收到确认或达到上限。</p>
 */
public final class TournamentHandshakeClient {

    /** 每 20 tick（约 1 秒）重发一次，最多重发 {@code MAX_ATTEMPTS} 次。 */
    private static final int RETRY_TICKS = 20;
    private static final int MAX_ATTEMPTS = 4;

    private static boolean verified;
    private static int attempts;
    private static int tickCount;

    private TournamentHandshakeClient() {
    }

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(
                TournamentTextPayload.TYPE, TournamentTextPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                TournamentTextPayload.TYPE, TournamentTextPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(TournamentTextPayload.TYPE, (payload, context) -> {
            String text = payload.text() == null ? "" : payload.text();
            context.client().execute(() -> {
                if (text.startsWith("DFS_OK")) {
                    verified = true;
                } else if (text.startsWith("DFS_FAIL")) {
                    String reason = text.substring("DFS_FAIL".length()).trim();
                    var player = Minecraft.getInstance().player;
                    if (player != null) {
                        // 本地聊天栏显示：sendChat 会把消息发到全服公开聊天
                        player.sendSystemMessage(
                                net.minecraft.network.chat.Component.literal(
                                        "§e[DFS] 赛事握手失败: §c" + reason));
                    }
                }
            });
        });

        // 进入 / 离开服务器时复位握手状态。
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());

        // 未验证且未达上限时，每秒重发一次握手。
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getConnection() == null) {
                return;
            }
            if (verified || attempts >= MAX_ATTEMPTS) {
                return;
            }
            tickCount++;
            if (tickCount < RETRY_TICKS) {
                return;
            }
            tickCount = 0;
            attempts++;
            sendHandshake();
        });
    }

    private static void sendHandshake() {
        try {
            // 服务器未注册该频道（原版服/无关服务器）时静默跳过，避免刷"未知 payload"日志
            if (!ClientPlayNetworking.canSend(TournamentTextPayload.TYPE)) {
                return;
            }
            String text = "DFS_HANDSHAKE 1 " + ModInfo.version() + " " + ModInfo.MOD_ID;
            ClientPlayNetworking.send(new TournamentTextPayload(text));
        } catch (Throwable ignored) {
            // 通道可能尚未 ready，下个 tick 再试。
        }
    }

    private static void reset() {
        verified = false;
        attempts = 0;
        tickCount = 0;
    }
}
