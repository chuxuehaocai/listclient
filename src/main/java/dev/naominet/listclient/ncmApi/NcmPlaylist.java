package dev.naominet.listclient.ncmApi;

/**
 * Lightweight playlist card for home / user playlist lists.
 */
public class NcmPlaylist {
    public long id;
    public String name = "";
    public String coverUrl = "";
    public String description = "";
    public long playCount;
    public int trackCount;
    public String creatorName = "";
    public boolean subscribed;
    public boolean specialTypeLiked; // "我喜欢的音乐" style playlist

    public String shortPlayCount() {
        if (playCount >= 100_000_000L) {
            return String.format("%.1f亿", playCount / 100_000_000.0);
        }
        if (playCount >= 10_000L) {
            return String.format("%.1f万", playCount / 10_000.0);
        }
        return String.valueOf(playCount);
    }
}
