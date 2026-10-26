package org.starset.dfsui.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 赛事握手文本 payload，内容为 UTF-8 文本行：客户端 → 服务端为
 * {@code "DFS_HANDSHAKE <protocol> <modVersion> <modId>"}，服务端 → 客户端为
 * {@code "DFS_OK <protocol>"} 或 {@code "DFS_FAIL <reason>"}。
 *
 * <p>请求与响应都走同一条 {@code deltaforcestrike:tournament} 动态/命名通道，
 * 与服务端 Paper plugin message 通道对齐；用动态通道是为兼容旧版服务端
 * 以 plugin message 字节流解析的方式，而非 Fabric 1.21 的自定义 payload 注册。</p>
 */
public record TournamentTextPayload(String text) implements CustomPacketPayload {

    public static final Identifier CHANNEL_ID =
            Identifier.fromNamespaceAndPath("deltaforcestrike", "tournament");

    public static final CustomPacketPayload.Type<TournamentTextPayload> TYPE =
            new CustomPacketPayload.Type<>(CHANNEL_ID);

    public static final StreamCodec<FriendlyByteBuf, TournamentTextPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> StringCodec.write(buf, payload.text()),
                    buf -> new TournamentTextPayload(StringCodec.read(buf))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
