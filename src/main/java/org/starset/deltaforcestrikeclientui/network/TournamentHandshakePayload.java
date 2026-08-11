package org.starset.deltaforcestrikeclientui.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * 赛事握手协议客户端 → 服务端 payload。
 * 内容为 UTF-8 文本行："DFS_HANDSHAKE <protocol> <modVersion> <modId>"。
 *
 * <p>使用动态 / 命名通道 {@link #CHANNEL_ID}，与 Paper 服务端的
 * plugin message channel "deltaforcestrike:tournament" 对齐。</p>
 *
 * <p>broker 选择 {@link org.starset.deltaforcestrikeclientui.network.TournamentHandshakePayload}
 * 而非 Fabric 1.21 的自定义 payload 注册，是为了兼容旧版服务端用 plugin message 通道
 * 接收字节流的解析逻辑。</p>
 */
public record TournamentHandshakePayload(String text) implements CustomPacketPayload {

    public static final Identifier CHANNEL_ID =
            Identifier.fromNamespaceAndPath("deltaforcestrike", "tournament");

    public static final CustomPacketPayload.Type<TournamentHandshakePayload> TYPE =
            new CustomPacketPayload.Type<>(CHANNEL_ID);

    public static final StreamCodec<FriendlyByteBuf, TournamentHandshakePayload> CODEC =
            StreamCodec.of(TournamentHandshakePayload::encode, TournamentHandshakePayload::decode);

    public static TournamentHandshakePayload decode(FriendlyByteBuf buf) {
        int len = buf.readInt();
        if (len < 0 || len > 32768) {
            return new TournamentHandshakePayload("");
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new TournamentHandshakePayload(new String(bytes, StandardCharsets.UTF_8));
    }

    public static void encode(FriendlyByteBuf buf, TournamentHandshakePayload payload) {
        byte[] bytes = (payload.text() == null ? "" : payload.text()).getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
