package dev.naominet.listclient.ncmApi;

/**
 * One timed lyric line. timeMs is the start offset in the track.
 */
public class NcmLyricLine {
    public long timeMs;
    public String text = "";

    public NcmLyricLine(long timeMs, String text) {
        this.timeMs = timeMs;
        this.text = text == null ? "" : text;
    }
}
