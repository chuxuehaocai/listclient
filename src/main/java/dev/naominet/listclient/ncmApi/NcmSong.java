package dev.naominet.listclient.ncmApi;

/**
 * Simplified song model shared across home, search, playlist and player.
 */
public class NcmSong {
    public long id;
    public String name = "";
    public String artists = "";
    public String album = "";
    public String coverUrl = "";
    /** Duration in milliseconds. */
    public int durationMs;
    public String playUrl = "";
    public boolean liked;

    public String durationText() {
        int totalSec = Math.max(0, durationMs / 1000);
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return String.format("%d:%02d", min, sec);
    }

    public String titleLine() {
        if (artists == null || artists.isEmpty()) {
            return name;
        }
        return name + " - " + artists;
    }

    public static NcmSong fromDetail(MusicDetail.Song song) {
        NcmSong s = new NcmSong();
        if (song == null) {
            return s;
        }
        s.id = song.id;
        s.name = song.name == null ? "" : song.name;
        s.durationMs = song.dt;
        if (song.al != null) {
            s.album = song.al.name == null ? "" : song.al.name;
            s.coverUrl = song.al.picUrl == null ? "" : song.al.picUrl;
        }
        if (song.ar != null && !song.ar.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < song.ar.size(); i++) {
                MusicDetail.Artist a = song.ar.get(i);
                if (a == null || a.name == null) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(" / ");
                }
                sb.append(a.name);
            }
            s.artists = sb.toString();
        }
        return s;
    }
}
