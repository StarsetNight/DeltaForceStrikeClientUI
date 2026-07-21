package org.starset.deltaforcestrikeclientui.client.hud;

import org.starset.deltaforcestrikeclientui.network.HudProtocol;

/**
 * 中下方局势横幅：下包 / 赛点 / 上半场最终局 / 决胜局。
 */
public final class SituationBanner {

    public enum Kind {
        NONE,
        BOMB,
        MATCH_POINT,
        FIRST_HALF_FINAL,
        DECISIVE
    }

    private Kind kind = Kind.NONE;
    private String line1 = "";
    private String line2 = "";
    private int edgeColor = 0xFFFFFFFF;
    private int bgColor = 0xC0101218;
    private long showUntilMs;
    private String lastBombKey = "";
    private String lastSitKey = "";

    public void tick(HudProtocol.HudSnapshot s) {
        long now = System.currentTimeMillis();
        if (!s.active()) {
            kind = Kind.NONE;
            lastBombKey = "";
            lastSitKey = "";
            return;
        }

        // 下包：仅首次安放弹出约 4 秒
        if (s.bombPlanted()) {
            int fuse = s.bombFuseLeft() >= 0 ? s.bombFuseLeft() : s.timerSeconds();
            String bombKey = s.currentRound() + ":planted";
            if (!bombKey.equals(lastBombKey)) {
                lastBombKey = bombKey;
                kind = Kind.BOMB;
                line1 = "炸弹已经放置";
                line2 = "距爆炸还有 " + Math.max(0, fuse) + " 秒";
                edgeColor = 0xFFFF3B3B;
                bgColor = 0xC0401010;
                showUntilMs = now + 4000L;
            } else if (kind == Kind.BOMB && now < showUntilMs) {
                line2 = "距爆炸还有 " + Math.max(0, fuse) + " 秒";
            }
        } else {
            lastBombKey = "";
            if (kind == Kind.BOMB) {
                kind = Kind.NONE;
            }
        }

        // 局势：购买阶段每回合首次弹出（下包横幅优先，不覆盖）
        if (kind != Kind.BOMB || now >= showUntilMs) {
            if ("BUY".equals(s.roundState()) && "IN_PROGRESS".equals(s.matchState())) {
                Situation sit = resolveSituation(s);
                if (sit != null) {
                    String sitKey = s.currentRound() + ":" + sit.kind.name();
                    if (!sitKey.equals(lastSitKey)) {
                        lastSitKey = sitKey;
                        kind = sit.kind;
                        line1 = sit.line1;
                        line2 = sit.line2;
                        edgeColor = sit.edge;
                        bgColor = sit.bg;
                        showUntilMs = now + 4500L;
                    }
                }
            }
        }

        if (kind != Kind.NONE && now >= showUntilMs) {
            kind = Kind.NONE;
        }
    }

    public boolean visible() {
        return kind != Kind.NONE && System.currentTimeMillis() < showUntilMs;
    }

    public Kind kind() {
        return kind;
    }

    public String line1() {
        return line1;
    }

    public String line2() {
        return line2;
    }

    public int edgeColor() {
        return edgeColor;
    }

    public int bgColor() {
        return bgColor;
    }

    private static Situation resolveSituation(HudProtocol.HudSnapshot s) {
        int winTarget = Math.max(1, s.winTarget());
        int halfRound = Math.max(1, s.halfRound());
        int round = s.currentRound();
        int scoreT = s.scoreT();
        int scoreCT = s.scoreCT();
        int needT = winTarget - scoreT;
        int needCT = winTarget - scoreCT;
        String team = s.myTeam() == null ? "NONE" : s.myTeam();

        boolean decisive = (needT == 1 && needCT == 2) || (needCT == 1 && needT == 2);
        boolean matchPoint = needT == 1 || needCT == 1;
        boolean firstHalfFinal = !s.halfTimeSwapped() && round == halfRound;

        int teamEdge = "T".equals(team) ? 0xFFE8A33C : ("CT".equals(team) ? 0xFF5B9BD5 : 0xFFE8C84A);
        int teamBg = "T".equals(team) ? 0xC0281C12 : ("CT".equals(team) ? 0xC0121C28 : 0xC0202010);

        if (decisive) {
            return new Situation(Kind.DECISIVE, "决胜局", "一方胜出，抑或双方战平",
                    teamEdge, teamBg);
        }
        if (matchPoint) {
            String sub;
            if (needT == 1 && needCT == 1) {
                sub = "双方均差 1 分胜利";
            } else if (needT == 1) {
                sub = "进攻方 T 再胜 1 分胜利";
            } else {
                sub = "防守方 CT 再胜 1 分胜利";
            }
            // 赛点：蓝框
            return new Situation(Kind.MATCH_POINT, "赛点", sub, 0xFF4A90E2, 0xC0121C30);
        }
        if (firstHalfFinal) {
            // 上半场最终局：黄框
            return new Situation(Kind.FIRST_HALF_FINAL, "上半场最终局",
                    "比分 T " + scoreT + " - " + scoreCT + " CT",
                    0xFFE8C84A, 0xC0282410);
        }
        return null;
    }

    private record Situation(Kind kind, String line1, String line2, int edge, int bg) {
    }
}
