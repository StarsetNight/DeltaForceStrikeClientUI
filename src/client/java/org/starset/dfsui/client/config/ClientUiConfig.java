package org.starset.dfsui.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.starset.dfsui.client.music.MusicEvent;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * {@code config/deltaforcestrikeclientui.json}
 * <p>
 * 每个事件可指向<strong>不同</strong>视频/音频文件；时间轴可选，不写则播全文件。
 * <pre>
 * "buy-1": {
 *   "file": "D:/media/buy_theme.mp4",
 *   "startSeconds": 12.5,
 *   "endSeconds": 45
 * }
 * "death": {
 *   "file": "D:/media/death.wav"
 * }
 * </pre>
 */
public final class ClientUiConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static ClientUiConfig instance = new ClientUiConfig();

    public float masterVolume = 0.7f;
    /** HUD 整体水平偏移（像素，正=右移） */
    public int hudOffsetX = 0;
    /** HUD 整体垂直偏移（像素，正=下移） */
    public int hudOffsetY = 0;
    /** HUD 整体缩放（0.5~3.0，1=原始大小） */
    public float hudScale = 1.0f;
    public final Map<MusicEvent, TrackConfig> tracks = new EnumMap<>(MusicEvent.class);

    public static ClientUiConfig get() {
        return instance;
    }

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("deltaforcestrikeclientui.json");
    }

    /**
     * @return true 表示配置正常读取（或已新建默认配置）；false 表示解析失败，
     *         已回退默认值并写了 .broken 备份。
     */
    public static boolean loadOrCreate() {
        Path path = configPath();
        try {
            if (!Files.exists(path)) {
                instance = defaults();
                save();
                return true;
            }
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                instance = fromJson(root);
            }
            // 合并新增事件键后写回，方便用户看到 combat-10s 等
            save();
            return true;
        } catch (Exception e) {
            // 解析失败：备份原文件并使用默认值，绝不覆写用户配置
            // （覆写会把用户填的音乐包路径全部抹掉）
            instance = defaults();
            var logger = com.mojang.logging.LogUtils.getLogger();
            try {
                Path backup = path.resolveSibling("deltaforcestrikeclientui.json.broken");
                Files.copy(path, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                logger.error("[DFS-UI] 配置解析失败，已备份原文件到 {} 并使用默认配置。"
                        + "修复后可用 /dfscui reload 重新加载", backup, e);
            } catch (IOException ioEx) {
                logger.error("[DFS-UI] 配置解析失败且备份失败，使用默认配置（原文件未改动）", ioEx);
            }
            return false;
        }
    }

    public static void save() throws IOException {
        Path path = configPath();
        Files.createDirectories(path.getParent());
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(instance.toJson(), w);
        }
    }

    /**
     * @return 同 {@link #loadOrCreate()}：false 表示解析失败并已回退默认值。
     */
    public static boolean reload() throws IOException {
        return loadOrCreate();
    }

    private static ClientUiConfig defaults() {
        ClientUiConfig c = new ClientUiConfig();
        c.masterVolume = 0.7f;
        for (MusicEvent e : MusicEvent.values()) {
            c.tracks.put(e, new TrackConfig());
        }
        return c;
    }

    private JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("masterVolume", masterVolume);
        JsonObject hud = new JsonObject();
        hud.addProperty("offset-x", hudOffsetX);
        hud.addProperty("offset-y", hudOffsetY);
        hud.addProperty("scale", hudScale);
        root.add("hud", hud);
        JsonObject music = new JsonObject();
        for (MusicEvent e : MusicEvent.values()) {
            TrackConfig t = tracks.getOrDefault(e, new TrackConfig());
            JsonObject o = new JsonObject();
            o.addProperty("file", t.file == null ? "" : t.file);
            // 时间轴：仅在有值时写出；null 表示不限制（全文件）
            if (t.startSeconds != null) {
                o.addProperty("startSeconds", t.startSeconds);
            }
            if (t.endSeconds != null) {
                o.addProperty("endSeconds", t.endSeconds);
            }
            music.add(e.configKey, o);
        }
        root.add("music", music);
        return root;
    }

    private static ClientUiConfig fromJson(JsonObject root) {
        ClientUiConfig c = defaults();
        if (root.has("masterVolume")) {
            float v = root.get("masterVolume").getAsFloat();
            // NaN 会在后续 Math.min/max 链路中穿透，钳制并归一
            c.masterVolume = Float.isNaN(v) ? 0.7f : Math.max(0f, Math.min(1f, v));
        }
        if (root.has("hud") && root.get("hud").isJsonObject()) {
            JsonObject hud = root.getAsJsonObject("hud");
            c.hudOffsetX = readInt(hud, "offset-x", 0, -500, 500);
            c.hudOffsetY = readInt(hud, "offset-y", 0, -500, 500);
            float scale = readDouble(hud, "scale", 1.0f);
            c.hudScale = Float.isNaN(scale) ? 1.0f
                    : Math.max(0.5f, Math.min(3.0f, scale));
        }
        JsonObject music = root.has("music") && root.get("music").isJsonObject()
                ? root.getAsJsonObject("music") : new JsonObject();
        for (MusicEvent e : MusicEvent.values()) {
            TrackConfig t = new TrackConfig();
            if (music.has(e.configKey) && music.get(e.configKey).isJsonObject()) {
                JsonObject o = music.getAsJsonObject(e.configKey);
                if (o.has("file") && !o.get("file").isJsonNull()) {
                    t.file = o.get("file").getAsString();
                }
                t.startSeconds = readOptionalDouble(o, "startSeconds");
                t.endSeconds = readOptionalDouble(o, "endSeconds");
            }
            c.tracks.put(e, t);
        }
        return c;
    }

    /**
     * 缺省 / null / 0 → 视为未指定（start=0 从头，end=到文件尾）。
     * 正数才作为时间轴边界。
     */
    private static Double readOptionalDouble(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = o.get(key);
        if (!el.isJsonPrimitive()) {
            return null;
        }
        try {
            double v = el.getAsDouble();
            // NaN/Infinity 一律视为未指定，避免 ffplay 收到 "-ss NaN"
            if (!Double.isFinite(v) || v <= 0.0) {
                return null;
            }
            return v;
        } catch (NumberFormatException | UnsupportedOperationException e) {
            return null;
        }
    }

    private static int readInt(JsonObject o, String key, int def, int min, int max) {
        if (!o.has(key) || o.get(key).isJsonNull() || !o.get(key).isJsonPrimitive()) {
            return def;
        }
        try {
            int v = o.get(key).getAsInt();
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static float readDouble(JsonObject o, String key, float def) {
        if (!o.has(key) || o.get(key).isJsonNull() || !o.get(key).isJsonPrimitive()) {
            return def;
        }
        try {
            return o.get(key).getAsFloat();
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public TrackConfig track(MusicEvent event) {
        return tracks.getOrDefault(event, new TrackConfig());
    }

    public static final class TrackConfig {
        /**
         * 媒体文件路径（每个事件可不同）。
         * 支持：绝对路径，或相对游戏目录 / config/deltaforcestrikeclientui/media|music/
         * 格式：视频 mp4/mkv/webm/avi/mov（需 ffplay）或 音频 wav/aiff；mp3 等也走 ffplay。
         */
        public String file = "";
        /**
         * 起点秒。null 或不写 / ≤0 = 从头。
         */
        public Double startSeconds = null;
        /**
         * 终点秒。null 或不写 / ≤0 = 播到文件结束。
         * 事件提前结束时仍会掐断。
         */
        public Double endSeconds = null;

        public boolean hasFile() {
            return file != null && !file.isBlank();
        }

        public double startOrZero() {
            return startSeconds == null || startSeconds < 0 ? 0.0 : startSeconds;
        }

        /** null = 到文件尾 */
        public Double endOrNull() {
            if (endSeconds == null || endSeconds <= 0) {
                return null;
            }
            return endSeconds;
        }
    }
}
