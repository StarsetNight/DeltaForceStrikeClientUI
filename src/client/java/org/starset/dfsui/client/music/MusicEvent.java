package org.starset.dfsui.client.music;

/**
 * 配置文件 music 节点键名。
 */
public enum MusicEvent {
    WAITING("waiting"),
    BUY_1("buy-1"),
    BUY_2("buy-2"),
    BUY_3("buy-3"),
    ROUND_START_1("round-start-1"),
    ROUND_START_2("round-start-2"),
    ROUND_START_3("round-start-3"),
    COMBAT_10S("combat-10s"),
    BOMB_PLANTED("bomb-planted"),
    BOMB_10S("bomb-10s"),
    DEATH("death"),
    ROUND_WIN("round-win"),
    ROUND_LOSE("round-lose"),
    MATCH_END("match-end");

    public final String configKey;

    MusicEvent(String configKey) {
        this.configKey = configKey;
    }
}
