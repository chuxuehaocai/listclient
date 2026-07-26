package dev.naominet.listclient.ncmApi;

/**
 * Logged-in NetEase Cloud Music account profile used by the MusicPlayer UI.
 */
public class NcmUser {
    public long userId;
    public String nickname = "";
    public String avatarUrl = "";
    public String signature = "";
    public int level;
    public int vipType;
    public boolean loggedIn;

    public static NcmUser empty() {
        NcmUser user = new NcmUser();
        user.loggedIn = false;
        user.nickname = "未登录";
        return user;
    }

    public String displayName() {
        if (nickname == null || nickname.isEmpty()) {
            return loggedIn ? ("UID " + userId) : "未登录";
        }
        return nickname;
    }
}
