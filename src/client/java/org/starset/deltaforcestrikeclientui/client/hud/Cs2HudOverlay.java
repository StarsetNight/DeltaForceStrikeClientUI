package org.starset.deltaforcestrikeclientui.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.starset.deltaforcestrikeclientui.network.HudProtocol;

/**
 * 仿 CS2 对局 HUD：顶栏比分/计时、最左下金钱、左侧中部队友、中下局势横幅。
 */
public final class Cs2HudOverlay implements HudElement {

    private static final int COL_PANEL = 0xC0101218;
    private static final int COL_PANEL_EDGE = 0xFF2A2E38;
    private static final int COL_T = 0xFFE8A33C;
    private static final int COL_CT = 0xFF5B9BD5;
    private static final int COL_MONEY = 0xFF6FCF6F;
    private static final int COL_WHITE = 0xFFFFFFFF;
    private static final int COL_DIM = 0xFFB0B4BE;
    private static final int COL_DEAD = 0xFF6A6E78;
    private static final int COL_BOMB = 0xFFFF3B3B;
    private static final int COL_HP_BG = 0xFF1A1D24;
    private static final int COL_HP = 0xFF3DDC84;
    private static final int COL_HP_LOW = 0xFFE8A33C;
    private static final int COL_HP_CRIT = 0xFFFF4D4D;

    private final SituationBanner banner = new SituationBanner();

    /** 客户端本地读条：服务端给 elapsed/total tick，本地按墙钟匀速 */
    private boolean localChannel;
    private String localChannelType = "";
    private String localChannelPlayer = "";
    private long localChannelStartMs;
    private int localChannelTotalTicks;
    private int lastSyncElapsedTicks = -1;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || mc.player == null) {
            return;
        }
        HudProtocol.HudSnapshot s = ClientHudState.get();
        if (!s.active()) {
            localChannel = false;
            return;
        }

        updateLocalChannel(s);
        banner.tick(s);

        Font font = mc.font;
        int sw = g.guiWidth();
        int sh = g.guiHeight();

        drawTopScoreboard(g, font, sw, s);
        drawMoney(g, font, sh, s);
        drawTeammates(g, font, sh, s);
        drawMidBanner(g, font, sw, sh, s);
        drawChannelBar(g, font, sw, sh);
    }

    private void drawTopScoreboard(GuiGraphicsExtractor g, Font font, int sw, HudProtocol.HudSnapshot s) {
        String tScore = String.valueOf(s.scoreT());
        String ctScore = String.valueOf(s.scoreCT());
        int displaySec = predictedSeconds(s);
        String timer = formatTimer(displaySec, s.bombPlanted());
        String round = "R" + Math.max(0, s.currentRound());

        int boxH = 28;
        int scoreW = 34;
        int timerW = 56;
        int totalW = scoreW + timerW + scoreW;
        int x0 = (sw - totalW) / 2;
        int y0 = 8;

        fillPanel(g, x0 - 4, y0 - 2, x0 + totalW + 4, y0 + boxH + 2);

        g.fill(x0, y0, x0 + scoreW, y0 + boxH, 0xE0281C12);
        drawCentered(g, font, tScore, x0 + scoreW / 2, y0 + 9, COL_T);

        int tx = x0 + scoreW;
        g.fill(tx, y0, tx + timerW, y0 + boxH, 0xE012141A);
        int timerColor = s.bombPlanted() ? COL_BOMB : COL_WHITE;
        if (!s.bombPlanted() && displaySec <= 10 && "COMBAT".equals(s.roundState())) {
            timerColor = COL_BOMB;
        }
        drawCentered(g, font, timer, tx + timerW / 2, y0 + 5, timerColor);
        drawCentered(g, font, round, tx + timerW / 2, y0 + 16, COL_DIM);

        int cx = tx + timerW;
        g.fill(cx, y0, cx + scoreW, y0 + boxH, 0xE0121C28);
        drawCentered(g, font, ctScore, cx + scoreW / 2, y0 + 9, COL_CT);

        String team = s.myTeam() == null ? "NONE" : s.myTeam();
        if ("T".equals(team) || "CT".equals(team)) {
            String label = "T".equals(team) ? "T" : "CT";
            int color = "T".equals(team) ? COL_T : COL_CT;
            g.text(font, label, x0 - 14, y0 + 10, color, true);
        }
    }

    /** 最左下角，避开聊天栏（通常在偏左下偏上区域） */
    private void drawMoney(GuiGraphicsExtractor g, Font font, int sh, HudProtocol.HudSnapshot s) {
        String money = "$" + s.money();
        int x = 4;
        int y = sh - 22;
        int w = Math.max(48, font.width(money) + 12);
        fillPanel(g, x, y, x + w, y + 16);
        g.text(font, money, x + 6, y + 4, COL_MONEY, true);
    }

    /** 屏幕左侧中部，避开左上雷达 */
    private void drawTeammates(GuiGraphicsExtractor g, Font font, int sh, HudProtocol.HudSnapshot s) {
        var mates = s.teammates();
        if (mates.isEmpty()) {
            return;
        }
        int cardW = 112;
        int cardH = 22;
        int gap = 3;
        int x = 4;
        int max = Math.min(mates.size(), 6);
        int blockH = max * (cardH + gap) - gap;
        int startY = Math.max(90, (sh - blockH) / 2 - 10);

        for (int i = 0; i < max; i++) {
            HudProtocol.TeammateInfo m = mates.get(i);
            int y = startY + i * (cardH + gap);
            boolean dead = !m.alive();
            int bg = dead ? 0xB0181010 : COL_PANEL;
            g.fill(x, y, x + cardW, y + cardH, bg);
            g.fill(x, y, x + 2, y + cardH, teamAccent(s.myTeam()));

            int nameColor = dead ? COL_DEAD : (m.self() ? COL_WHITE : COL_DIM);
            String name = trim(m.name(), 10);
            if (m.hasBomb()) {
                name = "◎ " + name;
                nameColor = COL_BOMB;
            }
            g.text(font, name, x + 6, y + 3, nameColor, true);

            int barX = x + 6;
            int barY = y + 14;
            int barW = cardW - 12;
            int barH = 4;
            g.fill(barX, barY, barX + barW, barY + barH, COL_HP_BG);
            if (!dead) {
                int hp = Math.max(0, Math.min(100, m.healthPercent()));
                int fill = Math.max(0, barW * hp / 100);
                int hpColor = hp <= 25 ? COL_HP_CRIT : (hp <= 50 ? COL_HP_LOW : COL_HP);
                if (fill > 0) {
                    g.fill(barX, barY, barX + fill, barY + barH, hpColor);
                }
            } else {
                g.text(font, "DEAD", x + cardW - 28, y + 3, COL_DEAD, true);
            }
        }
    }

    private void drawMidBanner(GuiGraphicsExtractor g, Font font, int sw, int sh, HudProtocol.HudSnapshot s) {
        if (!banner.visible()) {
            return;
        }
        String l1 = banner.line1();
        // 炸弹横幅：秒数用客户端预测，避免卡顿感
        String l2 = banner.line2();
        if (banner.kind() == SituationBanner.Kind.BOMB && s.bombPlanted()) {
            int fuse = predictedSeconds(s);
            l2 = "距爆炸还有 " + Math.max(0, fuse) + " 秒";
        }
        int w1 = font.width(l1);
        int w2 = font.width(l2);
        int boxW = Math.max(w1, w2) + 28;
        int boxH = 36;
        int x0 = (sw - boxW) / 2;
        int y0 = (int) (sh * 0.62f);

        g.fill(x0, y0, x0 + boxW, y0 + boxH, banner.bgColor());
        g.outline(x0, y0, boxW, boxH, banner.edgeColor());
        drawCentered(g, font, l1, sw / 2, y0 + 8, banner.edgeColor());
        drawCentered(g, font, l2, sw / 2, y0 + 20, COL_WHITE);
    }

    private void updateLocalChannel(HudProtocol.HudSnapshot s) {
        if (!s.channelActive()) {
            localChannel = false;
            lastSyncElapsedTicks = -1;
            return;
        }
        // 仅自己读条时显示，队友/敌人操作不可见
        Minecraft mc = Minecraft.getInstance();
        String selfName = mc.player != null ? mc.player.getGameProfile().name() : "";
        String player = s.channelPlayer() == null ? "" : s.channelPlayer();
        if (selfName.isEmpty() || !selfName.equals(player)) {
            localChannel = false;
            lastSyncElapsedTicks = -1;
            return;
        }

        String type = s.channelType() == null ? "" : s.channelType();
        int totalTicks = s.channelTotalTicks() > 0
                ? s.channelTotalTicks()
                : ("PLANT".equalsIgnoreCase(type) ? 3 * 20 : 10 * 20);
        int elapsedTicks = Math.max(0, Math.min(totalTicks, s.channelElapsedTicks()));

        boolean newChannel = !localChannel
                || !type.equalsIgnoreCase(localChannelType)
                || !player.equals(localChannelPlayer)
                || localChannelTotalTicks != totalTicks;

        if (newChannel) {
            localChannel = true;
            localChannelType = type;
            localChannelPlayer = player;
            localChannelTotalTicks = totalTicks;
            lastSyncElapsedTicks = elapsedTicks;
            localChannelStartMs = System.currentTimeMillis() - (elapsedTicks * 50L);
            return;
        }

        if (elapsedTicks > lastSyncElapsedTicks) {
            lastSyncElapsedTicks = elapsedTicks;
            long impliedStart = System.currentTimeMillis() - (elapsedTicks * 50L);
            if (impliedStart < localChannelStartMs) {
                localChannelStartMs = impliedStart;
            }
        }
    }

    /** 仅安装/拆除读条；炸弹安放提示走中部横幅，不画爆炸进度条 */
    private void drawChannelBar(GuiGraphicsExtractor g, Font font, int sw, int sh) {
        if (!localChannel || localChannelTotalTicks <= 0) {
            return;
        }
        long totalMs = localChannelTotalTicks * 50L;
        long elapsedMs = System.currentTimeMillis() - localChannelStartMs;
        float progress = Math.max(0f, Math.min(1f, elapsedMs / (float) totalMs));

        boolean plant = "PLANT".equalsIgnoreCase(localChannelType);
        String label = plant ? "安装炸弹" : "拆除炸弹";
        int edgeColor = plant ? 0xFFFF7A1A : 0xFF4FE3FF;
        int fillColor = plant ? 0xFFE8A33C : 0xFF5B9BD5;
        int bgColor = 0xFF1A1D24;

        int barW = 220;
        int barH = 14;
        int x0 = (sw - barW) / 2;
        int y0 = (int) (sh * 0.78f);

        fillPanel(g, x0 - 4, y0 - 18, x0 + barW + 4, y0 + barH + 4);
        drawCentered(g, font, label, sw / 2, y0 - 14, edgeColor);
        g.fill(x0, y0, x0 + barW, y0 + barH, bgColor);
        int fillW = (int) (barW * progress);
        if (fillW > 0) {
            g.fill(x0, y0, x0 + fillW, y0 + barH, fillColor);
        }
        g.outline(x0, y0, barW, barH, edgeColor);
    }

    /**
     * 服务器每 0.5s 推送一次 timerSeconds；客户端按推送时刻向前推进，
     * 避免与服务器差一秒。
     */
    private static int predictedSeconds(HudProtocol.HudSnapshot s) {
        int base = s.timerSeconds();
        if (base <= 0) {
            return 0;
        }
        String rs = s.roundState();
        // 只对会递减的阶段做预测（购买/战斗/炸弹已安放用 fuse）
        boolean decreasing = "BUY".equals(rs) || "COMBAT".equals(rs)
                || ("BOMB_PLANTED".equals(rs) && s.bombPlanted());
        if (!decreasing) {
            return base;
        }
        long sinceMs = System.currentTimeMillis() - ClientHudState.lastUpdateMs();
        if (sinceMs < 0) {
            return base;
        }
        int advanced = (int) (sinceMs / 1000L);
        return Math.max(0, base - advanced);
    }

    private static String formatTimer(int sec, boolean bombPlanted) {
        sec = Math.max(0, sec);
        if (bombPlanted) {
            return String.valueOf(sec);
        }
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static int teamAccent(String team) {
        if ("T".equals(team)) {
            return COL_T;
        }
        if ("CT".equals(team)) {
            return COL_CT;
        }
        return COL_PANEL_EDGE;
    }

    private static void fillPanel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, COL_PANEL);
        g.outline(x0, y0, x1 - x0, y1 - y0, COL_PANEL_EDGE);
    }

    private static void drawCentered(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int color) {
        g.centeredText(font, text, cx, y, color);
    }

    private static String trim(String name, int max) {
        if (name == null) {
            return "";
        }
        if (name.length() <= max) {
            return name;
        }
        return name.substring(0, Math.max(1, max - 1)) + "…";
    }
}
