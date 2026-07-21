package org.starset.deltaforcestrikeclientui.client.hud;

import org.starset.deltaforcestrikeclientui.network.HudProtocol;

public final class ClientHudState {

    private static volatile HudProtocol.HudSnapshot snapshot = HudProtocol.HudSnapshot.inactive();
    private static volatile long lastUpdateMs;

    private ClientHudState() {
    }

    public static void apply(HudProtocol.HudSnapshot next) {
        snapshot = next == null ? HudProtocol.HudSnapshot.inactive() : next;
        lastUpdateMs = System.currentTimeMillis();
    }

    public static void clear() {
        snapshot = HudProtocol.HudSnapshot.inactive();
        lastUpdateMs = 0L;
    }

    public static HudProtocol.HudSnapshot get() {
        if (snapshot.active() && lastUpdateMs > 0
                && System.currentTimeMillis() - lastUpdateMs > 5000L) {
            return HudProtocol.HudSnapshot.inactive();
        }
        return snapshot;
    }

    public static long lastUpdateMs() {
        return lastUpdateMs;
    }
}
