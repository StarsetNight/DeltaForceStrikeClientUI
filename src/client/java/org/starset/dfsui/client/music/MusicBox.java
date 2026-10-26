package org.starset.dfsui.client.music;

import org.starset.dfsui.client.config.ClientUiConfig;
import org.starset.dfsui.client.hud.ClientHudState;
import org.starset.dfsui.network.HudProtocol;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 事件音乐状态机。
 * buy↔round-start、bomb↔bomb-10s 使用交叉渐变衔接。
 */
public final class MusicBox {

    private static final MusicBox INSTANCE = new MusicBox();

    private final MediaMusicPlayer player = new MediaMusicPlayer();

    private String lastMatchState = "";
    private String lastRoundState = "";
    private int lastRound = -1;
    /**
     * 初始为 false：JOIN/stopAll 后若首帧快照就是 alive=false
     * （中途重连/观战加入已死状态），不应误播死亡曲；
     * 只有先观测到存活、随后死亡才算真死亡转移。
     */
    private boolean lastAlive = false;
    private boolean lastBomb = false;
    private boolean bomb10Played = false;
    private boolean combat10Played = false;
    private boolean buyMusicPicked = false;
    private int buyVariant = -1;
    private String lastRoundResultKey = "";
    private MusicEvent activeEvent;

    private MusicBox() {
    }

    public static MusicBox get() {
        return INSTANCE;
    }

    public void stopAll() {
        player.stop();
        activeEvent = null;
        lastMatchState = "";
        lastRoundState = "";
        lastRound = -1;
        lastAlive = false;
        lastBomb = false;
        bomb10Played = false;
        combat10Played = false;
        buyMusicPicked = false;
        buyVariant = -1;
        lastRoundResultKey = "";
    }

    public void onConfigReload() {
        player.stop();
        activeEvent = null;
        buyMusicPicked = false;
        bomb10Played = false;
        combat10Played = false;
    }

    public void tick() {
        HudProtocol.HudSnapshot s = ClientHudState.get();
        if (!s.active()) {
            if (activeEvent != null || player.isPlaying()) {
                stopAll();
            }
            return;
        }

        if (activeEvent != null && !player.isPlaying()) {
            activeEvent = null;
        }

        String ms = nullToEmpty(s.matchState());
        String rs = nullToEmpty(s.roundState());
        int round = s.currentRound();
        boolean alive = s.alive();
        boolean bomb = s.bombPlanted();

        if ("ENDING".equals(ms) && !"ENDING".equals(lastMatchState)) {
            tryPlay(MusicEvent.MATCH_END, false, false);
        }

        if ("WAITING".equals(ms) || "COUNTDOWN".equals(ms) || "AGENT_SELECT".equals(ms)) {
            tryPlay(MusicEvent.WAITING, true, false);
        } else if (activeEvent == MusicEvent.WAITING) {
            player.stop();
            activeEvent = null;
        }

        if ("IN_PROGRESS".equals(ms) && "BUY".equals(rs)) {
            if (round != lastRound || !"BUY".equals(lastRoundState)) {
                buyMusicPicked = false;
                bomb10Played = false;
                combat10Played = false;
            }
            if (!buyMusicPicked) {
                buyMusicPicked = true;
                buyVariant = pickBuyVariant();
                if (buyVariant >= 0) {
                    tryPlay(buyEvent(buyVariant), true, false);
                }
            }
        }

        if ("IN_PROGRESS".equals(ms) && "COMBAT".equals(rs) && !"COMBAT".equals(lastRoundState)) {
            combat10Played = false;
            // 回合开始必须切曲：先停购买循环，再强制播 round-start（可交叉）
            boolean fromBuy = activeEvent != null && isBuyEvent(activeEvent);
            if (fromBuy) {
                // 不在这里 stop，交给 crossfade；若无 round-start 文件再硬停
            }
            MusicEvent start = null;
            if (buyVariant >= 0) {
                MusicEvent paired = startEvent(buyVariant);
                if (hasFile(paired)) {
                    start = paired;
                }
            }
            if (start == null) {
                start = firstConfigured(MusicEvent.ROUND_START_1, MusicEvent.ROUND_START_2, MusicEvent.ROUND_START_3);
            }
            if (start != null) {
                forcePlay(start, false, fromBuy);
            } else if (fromBuy || (activeEvent != null && isBuyEvent(activeEvent))) {
                player.stop();
                activeEvent = null;
            }
        }

        if ("IN_PROGRESS".equals(ms) && "COMBAT".equals(rs) && !bomb) {
            int left = s.timerSeconds();
            if (left <= 10 && left >= 0 && !combat10Played) {
                combat10Played = true;
                tryPlay(MusicEvent.COMBAT_10S, false, false);
            }
        }

        if (bomb && !lastBomb) {
            bomb10Played = false;
            tryPlay(MusicEvent.BOMB_PLANTED, false, false);
        }

        if (bomb) {
            int fuse = s.bombFuseLeft() >= 0 ? s.bombFuseLeft() : s.timerSeconds();
            if (fuse <= 10 && fuse >= 0 && !bomb10Played) {
                bomb10Played = true;
                // 炸弹曲 → 10秒警告：交叉渐变
                boolean fromBomb = activeEvent == MusicEvent.BOMB_PLANTED;
                tryPlay(MusicEvent.BOMB_10S, false, fromBomb);
            }
        } else {
            bomb10Played = false;
        }

        boolean roundResultThisTick = false;
        if ("ROUND_END".equals(rs)) {
            String winner = nullToEmpty(s.lastRoundWinner());
            String team = nullToEmpty(s.myTeam());
            String key = round + ":" + winner + ":" + team;
            if (!key.equals(lastRoundResultKey) && !winner.isEmpty()
                    && ("T".equals(team) || "CT".equals(team))) {
                lastRoundResultKey = key;
                roundResultThisTick = true;
                if (winner.equals(team)) {
                    tryPlay(MusicEvent.ROUND_WIN, false, false);
                } else {
                    tryPlay(MusicEvent.ROUND_LOSE, false, false);
                }
            }
        }

        if (lastAlive && !alive && !roundResultThisTick && !"ROUND_END".equals(rs)) {
            tryPlay(MusicEvent.DEATH, false, false);
        }

        // 购买阶段结束：若未切入回合开始曲，再掐购买循环
        if (activeEvent != null && isBuyEvent(activeEvent) && !"BUY".equals(rs)
                && !"COMBAT".equals(rs)) {
            player.stop();
            activeEvent = null;
        }

        player.tick();

        lastMatchState = ms;
        lastRoundState = rs;
        lastRound = round;
        lastAlive = alive;
        lastBomb = bomb;
    }

    private boolean tryPlay(MusicEvent event, boolean loop, boolean crossfade) {
        if (!hasFile(event)) {
            return false;
        }
        if (activeEvent != null && player.isPlaying()) {
            if (priority(event) < priority(activeEvent)) {
                return false;
            }
            if (event == activeEvent && loop) {
                return true;
            }
        }
        return startPlay(event, loop, crossfade);
    }

    /** 忽略优先级，必须切换（回合开始用） */
    private boolean forcePlay(MusicEvent event, boolean loop, boolean crossfade) {
        if (!hasFile(event)) {
            return false;
        }
        return startPlay(event, loop, crossfade);
    }

    private boolean startPlay(MusicEvent event, boolean loop, boolean crossfade) {
        ClientUiConfig.TrackConfig track = ClientUiConfig.get().track(event);
        float vol = ClientUiConfig.get().masterVolume;
        activeEvent = event;
        if (crossfade) {
            player.crossfadeTo(track, vol, loop);
        } else {
            player.play(track, vol, loop);
        }
        return true;
    }

    private static int priority(MusicEvent e) {
        return switch (e) {
            case MATCH_END -> 200;
            case ROUND_START_1, ROUND_START_2, ROUND_START_3 -> 130;
            case BUY_1, BUY_2, BUY_3 -> 120;
            case ROUND_WIN, ROUND_LOSE -> 110;
            case BOMB_PLANTED, BOMB_10S -> 100;
            case COMBAT_10S -> 50;
            case DEATH -> 30;
            case WAITING -> 10;
        };
    }

    private static boolean isBuyEvent(MusicEvent e) {
        return e == MusicEvent.BUY_1 || e == MusicEvent.BUY_2 || e == MusicEvent.BUY_3;
    }

    private static MusicEvent buyEvent(int variant) {
        return switch (variant) {
            case 1 -> MusicEvent.BUY_1;
            case 2 -> MusicEvent.BUY_2;
            default -> MusicEvent.BUY_3;
        };
    }

    private static MusicEvent startEvent(int variant) {
        return switch (variant) {
            case 1 -> MusicEvent.ROUND_START_1;
            case 2 -> MusicEvent.ROUND_START_2;
            default -> MusicEvent.ROUND_START_3;
        };
    }

    private static int pickBuyVariant() {
        List<Integer> ok = new ArrayList<>();
        if (hasFile(MusicEvent.BUY_1)) {
            ok.add(1);
        }
        if (hasFile(MusicEvent.BUY_2)) {
            ok.add(2);
        }
        if (hasFile(MusicEvent.BUY_3)) {
            ok.add(3);
        }
        if (ok.isEmpty()) {
            return -1;
        }
        return ok.get(ThreadLocalRandom.current().nextInt(ok.size()));
    }

    private static MusicEvent firstConfigured(MusicEvent... options) {
        for (MusicEvent e : options) {
            if (hasFile(e)) {
                return e;
            }
        }
        return null;
    }

    private static boolean hasFile(MusicEvent event) {
        return ClientUiConfig.get().track(event).hasFile();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
