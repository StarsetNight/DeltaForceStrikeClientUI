package org.starset.dfsui.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 战局 HUD 数据包，承载 {@link HudProtocol.HudSnapshot}。 */
public record DfsHudPayload(HudProtocol.HudSnapshot snapshot) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DfsHudPayload> TYPE =
            new CustomPacketPayload.Type<>(HudProtocol.CHANNEL);

    public static final StreamCodec<FriendlyByteBuf, DfsHudPayload> CODEC = StreamCodec.of(
            (buf, payload) -> HudProtocol.encode(buf, payload.snapshot()),
            buf -> new DfsHudPayload(HudProtocol.decode(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
