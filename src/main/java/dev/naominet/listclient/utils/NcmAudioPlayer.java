package dev.naominet.listclient.utils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Process-wide singleton remote-audio player.
 * <p>
 * Only one clip can ever be active. Each {@link #playUrl} bumps a generation
 * counter so in-flight downloads from a previous track are discarded instead of
 * stacking audio.
 */
public final class NcmAudioPlayer {

    public static final NcmAudioPlayer INSTANCE = new NcmAudioPlayer();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Object lock = new Object();
    /** Bumped on every play/stop so stale async work bails out. */
    private final AtomicLong generation = new AtomicLong(0);

    private Clip clip;
    private volatile Thread worker;
    private volatile boolean playing;
    private volatile boolean loading;
    private volatile String lastError = "";
    private volatile long durationMs;
    private volatile boolean virtualMode;
    private volatile long virtualStartedAt;
    private volatile long virtualOffsetMs;
    private volatile float volume = 0.85f;
    private volatile String currentUrl = "";

    private Consumer<Boolean> onPlayingChanged;
    private Runnable onEnded;

    private NcmAudioPlayer() {
    }

    public void setOnPlayingChanged(Consumer<Boolean> cb) {
        this.onPlayingChanged = cb;
    }

    public void setOnEnded(Runnable cb) {
        this.onEnded = cb;
    }

    public boolean isPlaying() {
        if (virtualMode) {
            return playing && positionMs() < Math.max(1, durationMs);
        }
        return playing;
    }

    public boolean isLoading() {
        return loading;
    }

    public String getLastError() {
        return lastError;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public float getVolume() {
        return volume;
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
        applyVolume();
    }

    public long positionMs() {
        synchronized (lock) {
            if (virtualMode) {
                if (!playing) {
                    return Math.min(durationMs, virtualOffsetMs);
                }
                long pos = virtualOffsetMs + (System.currentTimeMillis() - virtualStartedAt);
                if (pos >= durationMs && durationMs > 0) {
                    playing = false;
                    virtualOffsetMs = durationMs;
                    firePlaying(false);
                    fireEnded();
                    return durationMs;
                }
                return pos;
            }
            if (clip == null) {
                return 0;
            }
            return clip.getMicrosecondPosition() / 1000L;
        }
    }

    public float progress() {
        long dur = durationMs;
        if (dur <= 0) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, positionMs() / (float) dur));
    }

    /**
     * Stop whatever is playing and start {@code url}. Concurrent calls are safe:
     * only the latest generation is allowed to open a Clip.
     */
    public void playUrl(String url, long knownDurationMs) {
        if (url == null || url.isEmpty()) {
            lastError = "空的播放地址";
            return;
        }

        final long gen = generation.incrementAndGet();
        Thread previousWorker = worker;
        if (previousWorker != null) previousWorker.interrupt();

        Clip oldClip;
        synchronized (lock) {
            oldClip = clip;
            clip = null;
            playing = false;
            virtualMode = false;
            virtualOffsetMs = 0;
            loading = true;
            lastError = "";
            durationMs = Math.max(0, knownDurationMs);
            currentUrl = url;
        }

        Thread nextWorker = new Thread(() -> runAudioWorker(
                url, knownDurationMs, gen, oldClip), "ncm-audio-" + gen);
        nextWorker.setDaemon(true);
        worker = nextWorker;
        nextWorker.start();
    }

    private void runAudioWorker(String url, long knownDurationMs, long gen, Clip oldClip) {
        closeClip(oldClip);
        if (!current(gen)) return;
        try {
            byte[] data = download(url);
            if (!current(gen)) return;
            openAndStart(data, knownDurationMs, gen);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            if (!current(gen)) return;
            enterVirtualMode(knownDurationMs, gen, ex);
        } finally {
            if (generation.get() == gen) loading = false;
        }
    }

    private boolean current(long gen) {
        return !Thread.currentThread().isInterrupted() && generation.get() == gen;
    }

    private void enterVirtualMode(long knownDurationMs, long gen, Exception ex) {
        synchronized (lock) {
            if (!current(gen)) return;
            virtualMode = true;
            virtualOffsetMs = 0;
            virtualStartedAt = System.currentTimeMillis();
            playing = true;
            durationMs = Math.max(Math.max(durationMs, knownDurationMs), 1);
            lastError = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        }
        firePlaying(true);
    }

    public void pause() {
        synchronized (lock) {
            if (virtualMode) {
                if (playing) {
                    virtualOffsetMs = positionMs();
                    playing = false;
                    firePlaying(false);
                }
                return;
            }
            if (clip != null && clip.isRunning()) {
                clip.stop();
                playing = false;
                firePlaying(false);
            }
        }
    }

    public void resume() {
        synchronized (lock) {
            if (virtualMode) {
                if (!playing && virtualOffsetMs < durationMs) {
                    virtualStartedAt = System.currentTimeMillis();
                    playing = true;
                    firePlaying(true);
                }
                return;
            }
            if (clip != null && !clip.isRunning()) {
                clip.start();
                playing = true;
                firePlaying(true);
            }
        }
    }

    public void toggle() {
        if (isPlaying()) {
            pause();
        } else {
            resume();
        }
    }

    public void stop() {
        generation.incrementAndGet();
        stopInternal(true, true);
        currentUrl = "";
        loading = false;
    }

    public void seekRatio(float ratio) {
        ratio = Math.max(0f, Math.min(1f, ratio));
        seekMs((long) (durationMs * ratio));
    }

    public void seekMs(long targetMs) {
        synchronized (lock) {
            long target = Math.max(0L, durationMs > 0 ? Math.min(durationMs, targetMs) : targetMs);
            if (virtualMode) {
                virtualOffsetMs = target;
                virtualStartedAt = System.currentTimeMillis();
                return;
            }
            if (clip != null) {
                clip.setMicrosecondPosition(target * 1000L);
            }
        }
    }

    private void stopInternal(boolean fire, boolean bumpIgnored) {
        synchronized (lock) {
            closeClipOnly();
            boolean wasPlaying = playing;
            playing = false;
            virtualMode = false;
            virtualOffsetMs = 0;
            if (fire && wasPlaying) {
                firePlaying(false);
            }
        }
    }

    private void closeClipOnly() {
        if (clip != null) {
            try {
                clip.stop();
                clip.flush();
                clip.close();
            } catch (Exception ignored) {
            }
            clip = null;
        }
    }

    private void closeClip(Clip clipToClose) {
        if (clipToClose != null) {
            try {
                clipToClose.stop();
                clipToClose.flush();
                clipToClose.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void openAndStart(byte[] data, long knownDurationMs, long gen) {
        try {
            BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(data));
            AudioInputStream in = AudioSystem.getAudioInputStream(bis);
            AudioFormat base = in.getFormat();

            AudioFormat decoded;
            if (base.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)
                    && base.getSampleSizeInBits() == 16) {
                decoded = base;
            } else {
                float sampleRate = base.getSampleRate() <= 0 ? 44100f : base.getSampleRate();
                int channels = Math.max(1, base.getChannels());
                decoded = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        sampleRate,
                        16,
                        channels,
                        channels * 2,
                        sampleRate,
                        false
                );
            }
            AudioInputStream din = decoded.equals(base) ? in : AudioSystem.getAudioInputStream(decoded, in);

            if (generation.get() != gen) {
                try {
                    din.close();
                } catch (Exception ignored) {
                }
                return;
            }

            DataLine.Info info = new DataLine.Info(Clip.class, din.getFormat());
            Clip newClip = (Clip) AudioSystem.getLine(info);
            newClip.open(din);
            newClip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    if (clip == newClip
                            && generation.get() == gen
                            && newClip.getMicrosecondPosition() >= newClip.getMicrosecondLength() - 50_000) {
                        playing = false;
                        firePlaying(false);
                        fireEnded();
                    }
                }
            });

            if (generation.get() != gen) {
                try {
                    newClip.close();
                } catch (Exception ignored) {
                }
                return;
            }

            clip = newClip;
            virtualMode = false;
            lastError = "";
            durationMs = newClip.getMicrosecondLength() / 1000L;
            if (durationMs <= 0) {
                durationMs = knownDurationMs;
            }
            applyVolume();
            newClip.start();
            playing = true;
            firePlaying(true);
        } catch (UnsupportedAudioFileException | IllegalArgumentException | LineUnavailableException | IOException ex) {
            if (generation.get() != gen) {
                return;
            }
            virtualMode = true;
            virtualOffsetMs = 0;
            virtualStartedAt = System.currentTimeMillis();
            durationMs = Math.max(knownDurationMs, 1);
            playing = true;
            lastError = "音频解码失败，已启用进度模拟: " + ex.getClass().getSimpleName();
            firePlaying(true);
        }
    }

    private void applyVolume() {
        synchronized (lock) {
            if (clip == null) return;
            try {
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    float min = gain.getMinimum();
                    float max = Math.min(gain.getMaximum(), 0f);
                    if (volume <= 0.001f) {
                        gain.setValue(min);
                    } else {
                        gain.setValue(min + (max - min) * volume);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static byte[] download(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) listclient/1.0")
                .header("Referer", "https://music.163.com/")
                .GET()
                .build();
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 400) {
            throw new IOException("下载音频失败 HTTP " + response.statusCode());
        }
        byte[] body = response.body();
        if (body == null || body.length == 0) {
            throw new IOException("音频内容为空");
        }
        return body;
    }

    private void firePlaying(boolean state) {
        Consumer<Boolean> cb = onPlayingChanged;
        if (cb != null) {
            cb.accept(state);
        }
    }

    private void fireEnded() {
        Runnable cb = onEnded;
        if (cb != null) {
            cb.run();
        }
    }
}
