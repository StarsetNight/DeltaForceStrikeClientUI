package org.starset.dfsui.client.hud;

import net.minecraft.client.Minecraft;
import org.starset.dfsui.network.HudProtocol;

/**
 * 客户端安装 / 拆除炸弹的本地读条状态。
 * <p>服务端每 0.5s 推送一次 {@code channelElapsedTicks / channelTotalTicks}，
 * 客户端按墙钟匀速推进；每次同步到更大的 elapsed 时向前校正起点，
 * 避免读条出现停顿或跳变。仅对玩家自己的操作显示，队友/敌人的操作不可见。</p>
 */
public final class ChannelProgress {

    private static final int TICK_MILLIS = 50;

    private boolean active;
    private String type = "";
    private String player = "";
    private long startMs;
    private int totalTicks;
    private int lastSyncElapsedTicks = -1;

    public void update(HudProtocol.HudSnapshot s) {
        if (!s.channelActive()) {
            reset();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String selfName = mc.player != null ? mc.player.getGameProfile().name() : "";
        String op = s.channelPlayer() == null ? "" : s.channelPlayer();
        if (selfName.isEmpty() || !selfName.equals(op)) {
            reset();
            return;
        }

        String newType = s.channelType() == null ? "" : s.channelType();
        int newTotal = s.channelTotalTicks() > 0
                ? s.channelTotalTicks()
                : ("PLANT".equalsIgnoreCase(newType) ? 3 * 20 : 10 * 20);
        int elapsed = Math.max(0, Math.min(newTotal, s.channelElapsedTicks()));

        boolean newChannel = !active
                || !newType.equalsIgnoreCase(type)
                || !op.equals(player)
                || totalTicks != newTotal;

        if (newChannel) {
            active = true;
            type = newType;
            player = op;
            totalTicks = newTotal;
            lastSyncElapsedTicks = elapsed;
            startMs = System.currentTimeMillis() - (elapsed * TICK_MILLIS);
            return;
        }

        if (elapsed > lastSyncElapsedTicks) {
            lastSyncElapsedTicks = elapsed;
            long impliedStart = System.currentTimeMillis() - (elapsed * TICK_MILLIS);
            if (impliedStart < startMs) {
                startMs = impliedStart;
            }
        }
    }

    public boolean visible() {
        return active && totalTicks > 0;
    }

    public float progress() {
        long totalMs = totalTicks * (long) TICK_MILLIS;
        long elapsedMs = System.currentTimeMillis() - startMs;
        return Math.max(0f, Math.min(1f, elapsedMs / (float) totalMs));
    }

    public String label() {
        return isPlant() ? "安装炸弹" : "拆除炸弹";
    }

    public int edgeColor() {
        return isPlant() ? 0xFFFF7A1A : 0xFF4FE3FF;
    }

    public int fillColor() {
        return isPlant() ? 0xFFE8A33C : 0xFF5B9BD5;
    }

    private boolean isPlant() {
        return "PLANT".equalsIgnoreCase(type);
    }

    private void reset() {
        active = false;
        lastSyncElapsedTicks = -1;
    }
}
