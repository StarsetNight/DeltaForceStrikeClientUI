package org.starset.dfsui.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 与服务端 {@code deltaforcestrike:hud} 插件频道二进制协议对齐。
 * 协议版本号参见 {@link #PROTOCOL}（当前 4：在 v2 基础上增加 winTarget / halfRound /
 * halfTimeSwapped，v3 增加频道读条，v4 将读条时长由"秒"改为主机端的 tick 计数）。
 */
public final class HudProtocol {

    public static final Identifier CHANNEL = Identifier.fromNamespaceAndPath("deltaforcestrike", "hud");
    public static final byte PROTOCOL = 4;

    private HudProtocol() {
    }

    /** 严格按 {@link #encode} 的字节顺序解码。 */
    public static HudSnapshot decode(FriendlyByteBuf buf) {
        byte version = buf.readByte();
        if (version < 1 || version > 4) {
            return HudSnapshot.inactive();
        }
        boolean active = buf.readBoolean();
        if (!active) {
            return HudSnapshot.inactive();
        }

        String matchState = StringCodec.read(buf);
        String roundState = StringCodec.read(buf);
        int scoreT = buf.readInt();
        int scoreCT = buf.readInt();
        int currentRound = buf.readInt();
        int timerSeconds = buf.readInt();
        boolean bombPlanted = buf.readBoolean();
        int bombFuseLeft = buf.readInt();
        String myTeam = StringCodec.read(buf);
        int money = buf.readInt();
        int kills = buf.readInt();
        int deaths = buf.readInt();
        boolean alive = buf.readBoolean();
        String operatorId = StringCodec.read(buf);

        int winTarget = 13;
        int halfRound = 12;
        boolean halfTimeSwapped = false;
        String lastRoundWinner = "";
        boolean channelActive = false;
        String channelType = "";
        String channelPlayer = "";
        int channelElapsedTicks = 0;
        int channelTotalTicks = 0;
        String planterName = "";
        if (version >= 2) {
            winTarget = buf.readInt();
            halfRound = buf.readInt();
            halfTimeSwapped = buf.readBoolean();
            lastRoundWinner = StringCodec.read(buf);
        }
        if (version >= 3) {
            channelActive = buf.readBoolean();
            channelType = StringCodec.read(buf);
            channelPlayer = StringCodec.read(buf);
            if (version >= 4) {
                channelElapsedTicks = buf.readInt();
                channelTotalTicks = buf.readInt();
            } else {
                // v3: 曾发送 durationSec，换算为 tick
                int durSec = buf.readInt();
                channelTotalTicks = Math.max(0, durSec) * 20;
                channelElapsedTicks = 0;
            }
            planterName = StringCodec.read(buf);
        }

        int mateCount = Math.max(0, Math.min(32, buf.readInt()));
        List<TeammateInfo> mates = new ArrayList<>(mateCount);
        for (int i = 0; i < mateCount; i++) {
            String name = StringCodec.read(buf);
            int hp = buf.readInt();
            boolean mateAlive = buf.readBoolean();
            boolean hasBomb = buf.readBoolean();
            boolean self = buf.readBoolean();
            String op = StringCodec.read(buf);
            mates.add(new TeammateInfo(name, hp, mateAlive, hasBomb, self, op));
        }

        return new HudSnapshot(
                true,
                matchState,
                roundState,
                scoreT,
                scoreCT,
                currentRound,
                timerSeconds,
                bombPlanted,
                bombFuseLeft,
                myTeam,
                money,
                kills,
                deaths,
                alive,
                operatorId,
                winTarget,
                halfRound,
                halfTimeSwapped,
                lastRoundWinner,
                channelActive,
                channelType,
                channelPlayer,
                channelElapsedTicks,
                channelTotalTicks,
                planterName,
                Collections.unmodifiableList(mates)
        );
    }

    /** 严格按服务端发送顺序编码；任何字段顺序改动都会破坏握手协议。 */
    public static void encode(FriendlyByteBuf buf, HudSnapshot snap) {
        buf.writeByte(PROTOCOL);
        buf.writeBoolean(snap.active());
        if (!snap.active()) {
            return;
        }
        StringCodec.write(buf, snap.matchState());
        StringCodec.write(buf, snap.roundState());
        buf.writeInt(snap.scoreT());
        buf.writeInt(snap.scoreCT());
        buf.writeInt(snap.currentRound());
        buf.writeInt(snap.timerSeconds());
        buf.writeBoolean(snap.bombPlanted());
        buf.writeInt(snap.bombFuseLeft());
        StringCodec.write(buf, snap.myTeam());
        buf.writeInt(snap.money());
        buf.writeInt(snap.kills());
        buf.writeInt(snap.deaths());
        buf.writeBoolean(snap.alive());
        StringCodec.write(buf, snap.operatorId());
        buf.writeInt(snap.winTarget());
        buf.writeInt(snap.halfRound());
        buf.writeBoolean(snap.halfTimeSwapped());
        StringCodec.write(buf, snap.lastRoundWinner());
        buf.writeBoolean(snap.channelActive());
        StringCodec.write(buf, snap.channelType());
        StringCodec.write(buf, snap.channelPlayer());
        buf.writeInt(snap.channelElapsedTicks());
        buf.writeInt(snap.channelTotalTicks());
        StringCodec.write(buf, snap.planterName());
        buf.writeInt(snap.teammates().size());
        for (TeammateInfo m : snap.teammates()) {
            StringCodec.write(buf, m.name());
            buf.writeInt(m.healthPercent());
            buf.writeBoolean(m.alive());
            buf.writeBoolean(m.hasBomb());
            buf.writeBoolean(m.self());
            StringCodec.write(buf, m.operatorId());
        }
    }

    public record TeammateInfo(
            String name,
            int healthPercent,
            boolean alive,
            boolean hasBomb,
            boolean self,
            String operatorId
    ) {
    }

    public record HudSnapshot(
            boolean active,
            String matchState,
            String roundState,
            int scoreT,
            int scoreCT,
            int currentRound,
            int timerSeconds,
            boolean bombPlanted,
            int bombFuseLeft,
            String myTeam,
            int money,
            int kills,
            int deaths,
            boolean alive,
            String operatorId,
            int winTarget,
            int halfRound,
            boolean halfTimeSwapped,
            String lastRoundWinner,
            boolean channelActive,
            String channelType,
            String channelPlayer,
            int channelElapsedTicks,
            int channelTotalTicks,
            String planterName,
            List<TeammateInfo> teammates
    ) {
        public static HudSnapshot inactive() {
            return new HudSnapshot(
                    false, "", "", 0, 0, 0, 0, false, -1,
                    "NONE", 0, 0, 0, false, "", 13, 12, false, "",
                    false, "", "", 0, 0, "", List.of()
            );
        }
    }
}
