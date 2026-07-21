package org.starset.deltaforcestrikeclientui.client.music;

import org.starset.deltaforcestrikeclientui.client.config.ClientUiConfig;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 双槽位播放器：支持交叉渐变（WAV Clip 可平滑；ffplay 近似叠播后掐旧）。
 */
public final class MediaMusicPlayer {

    private static final long CROSSFADE_MS = 1600L;

    private final Slot a = new Slot();
    private final Slot b = new Slot();
    private Slot primary = a;
    private Slot fading;
    private long fadeStartMs;
    private float masterVolume = 0.7f;

    public synchronized boolean isPlaying() {
        return primary.isPlaying() || (fading != null && fading.isPlaying());
    }

    public synchronized void play(ClientUiConfig.TrackConfig track, float masterVolume, boolean loop) {
        playInternal(track, masterVolume, loop, false);
    }

    public synchronized void crossfadeTo(ClientUiConfig.TrackConfig track, float masterVolume, boolean loop) {
        playInternal(track, masterVolume, loop, true);
    }

    private void playInternal(ClientUiConfig.TrackConfig track, float masterVolume, boolean loop, boolean crossfade) {
        this.masterVolume = Math.max(0f, Math.min(1f, masterVolume));
        if (track == null || !track.hasFile()) {
            return;
        }
        Path path = resolve(track.file);
        if (path == null || !Files.isRegularFile(path)) {
            System.err.println("[DFS-CUI] 媒体文件不存在: " + track.file);
            return;
        }

        if (!crossfade || !primary.isPlaying()) {
            stopAll();
            primary.open(path, track, this.masterVolume, loop, 1f);
            return;
        }

        // 交叉：旧轨进 fading，新轨用另一槽从 0 音量起
        Slot next = primary == a ? b : a;
        if (fading != null && fading != primary) {
            fading.stop();
        }
        fading = primary;
        primary = next;
        primary.open(path, track, this.masterVolume, loop, 0f);
        fadeStartMs = System.currentTimeMillis();
    }

    public synchronized void tick() {
        long now = System.currentTimeMillis();
        if (fading != null) {
            float t = Math.max(0f, Math.min(1f, (now - fadeStartMs) / (float) CROSSFADE_MS));
            primary.setGainLinear(t);
            fading.setGainLinear(1f - t);
            if (t >= 1f) {
                fading.stop();
                fading = null;
                primary.setGainLinear(1f);
            }
        }
        primary.tick();
        if (fading != null) {
            fading.tick();
        }
    }

    public synchronized void stop() {
        stopAll();
    }

    private void stopAll() {
        a.stop();
        b.stop();
        primary = a;
        fading = null;
    }

    private static final class Slot {
        private Clip clip;
        private Process process;
        private long startMicros;
        private long endMicros = -1L;
        private boolean loop;
        private ClientUiConfig.TrackConfig track;
        private float master = 0.7f;
        private float gainLinear = 1f;
        private long playStartedAtMs;
        private double segmentDurationSec = -1;
        private boolean ffplay;

        boolean isPlaying() {
            if (clip != null && clip.isOpen() && clip.isRunning()) {
                return true;
            }
            return process != null && process.isAlive();
        }

        void open(Path path, ClientUiConfig.TrackConfig track, float master, boolean loop, float initialGain) {
            stop();
            this.track = track;
            this.master = master;
            this.loop = loop;
            this.gainLinear = Math.max(0f, Math.min(1f, initialGain));
            this.playStartedAtMs = System.currentTimeMillis();

            double start = track.startOrZero();
            Double end = track.endOrNull();
            if (end != null && end > start) {
                segmentDurationSec = end - start;
            } else {
                segmentDurationSec = -1;
            }

            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (isClipFriendly(name) && playWithClip(path, start, end)) {
                setGainLinear(this.gainLinear);
                return;
            }
            if (playWithFfplay(path, start, end)) {
                return;
            }
            System.err.println("[DFS-CUI] 无法播放: " + path);
        }

        void tick() {
            if (clip != null) {
                tickClip();
                return;
            }
            if (process != null) {
                tickProcess();
            }
        }

        void stop() {
            if (clip != null) {
                try {
                    clip.stop();
                    clip.flush();
                    clip.close();
                } catch (Exception ignored) {
                }
                clip = null;
            }
            if (process != null) {
                try {
                    process.destroy();
                    if (process.isAlive()) {
                        process.destroyForcibly();
                    }
                    process.waitFor(300, TimeUnit.MILLISECONDS);
                } catch (Exception ignored) {
                }
                process = null;
            }
            endMicros = -1L;
            startMicros = 0L;
            loop = false;
            track = null;
            segmentDurationSec = -1;
            ffplay = false;
        }

        void setGainLinear(float linear) {
            this.gainLinear = Math.max(0f, Math.min(1f, linear));
            if (clip != null) {
                applyVolume(clip, master * this.gainLinear);
            }
            // ffplay 进程音量启动后难改，交叉时叠播约 1.6s 后掐旧
        }

        private void tickClip() {
            Clip c = clip;
            if (c == null) {
                return;
            }
            if (!c.isOpen()) {
                stop();
                return;
            }
            if (endMicros > 0 && c.getMicrosecondPosition() >= endMicros) {
                if (loop) {
                    c.setMicrosecondPosition(startMicros);
                    if (!c.isRunning()) {
                        c.start();
                    }
                } else {
                    stop();
                }
            } else if (loop && !c.isRunning()) {
                c.setMicrosecondPosition(startMicros);
                c.start();
            }
        }

        private void tickProcess() {
            Process p = process;
            if (p == null) {
                return;
            }
            if (segmentDurationSec > 0) {
                long elapsed = System.currentTimeMillis() - playStartedAtMs;
                if (elapsed >= (long) (segmentDurationSec * 1000.0) + 200L) {
                    if (loop && track != null) {
                        ClientUiConfig.TrackConfig t = track;
                        float m = master;
                        boolean l = loop;
                        float g = gainLinear;
                        Path path = resolve(t.file);
                        stop();
                        if (path != null) {
                            open(path, t, m, l, g);
                        }
                    } else {
                        stop();
                    }
                    return;
                }
            }
            if (!p.isAlive()) {
                if (loop && track != null) {
                    ClientUiConfig.TrackConfig t = track;
                    float m = master;
                    boolean l = loop;
                    float g = gainLinear;
                    Path path = resolve(t.file);
                    stop();
                    if (path != null) {
                        open(path, t, m, l, g);
                    }
                } else {
                    process = null;
                }
            }
        }

        private boolean playWithClip(Path path, double startSec, Double endSec) {
            try {
                AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(path)));
                AudioFormat base = in.getFormat();
                AudioFormat decoded = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        base.getSampleRate(),
                        16,
                        base.getChannels(),
                        base.getChannels() * 2,
                        base.getSampleRate(),
                        false
                );
                AudioInputStream din = AudioSystem.isConversionSupported(decoded, base)
                        ? AudioSystem.getAudioInputStream(decoded, in) : in;

                DataLine.Info info = new DataLine.Info(Clip.class, din.getFormat());
                Clip c = (Clip) AudioSystem.getLine(info);
                c.open(din);

                long len = c.getMicrosecondLength();
                startMicros = (long) (Math.max(0.0, startSec) * 1_000_000.0);
                endMicros = endSec != null && endSec > 0
                        ? (long) (endSec * 1_000_000.0)
                        : len;
                if (startMicros >= len) {
                    startMicros = 0;
                }
                if (endMicros <= startMicros) {
                    endMicros = len;
                }

                applyVolume(c, master * gainLinear);
                c.setMicrosecondPosition(startMicros);
                this.clip = c;
                this.ffplay = false;
                c.start();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private boolean playWithFfplay(Path path, double startSec, Double endSec) {
            String bin = findFfplay();
            if (bin == null) {
                return false;
            }
            try {
                List<String> cmd = new ArrayList<>();
                cmd.add(bin);
                cmd.add("-nodisp");
                cmd.add("-autoexit");
                cmd.add("-loglevel");
                cmd.add("error");
                cmd.add("-volume");
                cmd.add(String.valueOf(Math.round(master * gainLinear * 100)));
                if (startSec > 0.001) {
                    cmd.add("-ss");
                    cmd.add(formatSec(startSec));
                }
                if (endSec != null && endSec > startSec) {
                    cmd.add("-t");
                    cmd.add(formatSec(endSec - startSec));
                }
                cmd.add(path.toAbsolutePath().toString());

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                this.process = pb.start();
                this.playStartedAtMs = System.currentTimeMillis();
                this.ffplay = true;
                return true;
            } catch (IOException e) {
                return false;
            }
        }
    }

    private static String formatSec(double sec) {
        if (sec < 0) {
            sec = 0;
        }
        return String.format(Locale.US, "%.3f", sec);
    }

    private static boolean isClipFriendly(String lowerName) {
        return lowerName.endsWith(".wav")
                || lowerName.endsWith(".aiff")
                || lowerName.endsWith(".aif")
                || lowerName.endsWith(".au");
    }

    private static String findFfplay() {
        String[] candidates = {
                "ffplay",
                "ffplay.exe",
                System.getenv("FFMPEG_HOME") != null ? System.getenv("FFMPEG_HOME") + "\\bin\\ffplay.exe" : null,
                System.getenv("FFMPEG_HOME") != null ? System.getenv("FFMPEG_HOME") + "/bin/ffplay" : null
        };
        for (String c : candidates) {
            if (c == null || c.isBlank()) {
                continue;
            }
            try {
                Process p = new ProcessBuilder(c, "-version")
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
                if (p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return c;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void applyVolume(Clip clip, float vol) {
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float min = gain.getMinimum();
                float max = gain.getMaximum();
                float db = vol <= 0.0001f ? min : (float) (20.0 * Math.log10(vol));
                gain.setValue(Math.max(min, Math.min(max, db)));
            }
        } catch (Exception ignored) {
        }
    }

    static Path resolve(String file) {
        if (file == null || file.isBlank()) {
            return null;
        }
        Path p = Paths.get(file);
        if (p.isAbsolute() && Files.isRegularFile(p)) {
            return p;
        }
        Path cwd = Paths.get("").toAbsolutePath().resolve(file);
        if (Files.isRegularFile(cwd)) {
            return cwd;
        }
        try {
            Path cfg = ClientUiConfig.configPath().getParent().resolve(file);
            if (Files.isRegularFile(cfg)) {
                return cfg;
            }
            Path media = ClientUiConfig.configPath().getParent()
                    .resolve("deltaforcestrikeclientui").resolve("media").resolve(file);
            if (Files.isRegularFile(media)) {
                return media;
            }
            Path music = ClientUiConfig.configPath().getParent()
                    .resolve("deltaforcestrikeclientui").resolve("music").resolve(file);
            if (Files.isRegularFile(music)) {
                return music;
            }
        } catch (Exception ignored) {
        }
        return p;
    }
}
