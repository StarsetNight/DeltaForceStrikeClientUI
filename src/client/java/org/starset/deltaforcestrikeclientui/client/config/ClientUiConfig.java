package org.starset.deltaforcestrikeclientui.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.starset.deltaforcestrikeclientui.client.music.MusicEvent;

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
    public final Map<MusicEvent, TrackConfig> tracks = new EnumMap<>(MusicEvent.class);

    public static ClientUiConfig get() {
        return instance;
    }

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("deltaforcestrikeclientui.json");
    }

    public static void loadOrCreate() {
        Path path = configPath();
        try {
            if (!Files.exists(path)) {
                instance = defaults();
                save();
                return;
            }
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                instance = fromJson(root);
            }
            // 合并新增事件键后写回，方便用户看到 combat-10s 等
            save();
        } catch (Exception e) {
            instance = defaults();
            try {
                save();
            } catch (IOException ignored) {
            }
        }
    }

    public static void save() throws IOException {
        Path path = configPath();
        Files.createDirectories(path.getParent());
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(instance.toJson(), w);
        }
    }

    public static void reload() throws IOException {
        loadOrCreate();
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
            c.masterVolume = root.get("masterVolume").getAsFloat();
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
        double v = el.getAsDouble();
        if (v <= 0.0) {
            return null;
        }
        return v;
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
