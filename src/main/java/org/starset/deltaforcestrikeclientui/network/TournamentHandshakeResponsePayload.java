package org.starset.deltaforcestrikeclientui.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.nio.charset.StandardCharsets;

/**
 * 赛事握手服务端 → 客户端响应 payload。
 * 内容为 UTF-8 文本行："DFS_OK <protocol>" 或 "DFS_FAIL <reason>"。
 * 与 {@link TournamentHandshakePayload#CHANNEL_ID} 同频道。
 */
public record TournamentHandshakeResponsePayload(String text) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TournamentHandshakeResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(TournamentHandshakePayload.CHANNEL_ID);

    public static final StreamCodec<FriendlyByteBuf, TournamentHandshakeResponsePayload> CODEC =
            StreamCodec.of(TournamentHandshakeResponsePayload::encode,
                    TournamentHandshakeResponsePayload::decode);

    public static TournamentHandshakeResponsePayload decode(FriendlyByteBuf buf) {
        int len = buf.readInt();
        if (len < 0 || len > 32768) {
            return new TournamentHandshakeResponsePayload("");
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new TournamentHandshakeResponsePayload(new String(bytes, StandardCharsets.UTF_8));
    }

    public static void encode(FriendlyByteBuf buf, TournamentHandshakeResponsePayload payload) {
        byte[] bytes = (payload.text() == null ? "" : payload.text()).getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
