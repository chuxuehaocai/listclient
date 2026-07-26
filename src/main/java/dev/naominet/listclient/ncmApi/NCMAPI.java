package dev.naominet.listclient.ncmApi;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.naominet.listclient.utils.DynamicImageUtils;
import dev.naominet.listclient.utils.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thin client for NeteaseCloudMusicApiEnhanced.
 * <p>
 * Cookie is kept in-memory and also persisted under {@code List/ncm_cookie.txt}
 * so QR login survives restarts. Every request appends a timestamp query param
 * to defeat intermediate caches (required by the QR login docs).
 */
public class NCMAPI {
    public final String BASE_URL;
    public final HttpClient httpClient;
    public final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ncm-api");
        t.setDaemon(true);
        return t;
    });

    private final Gson gson = new Gson();
    private final Object cookieLock = new Object();
    private volatile String cookie = "";

    private static final Pattern LRC_LINE = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?](.*)");
    private static final File COOKIE_FILE = new File("List", "ncm_cookie.txt");

    public NCMAPI(String BASE_URL) {
        this.BASE_URL = BASE_URL.endsWith("/") ? BASE_URL.substring(0, BASE_URL.length() - 1) : BASE_URL;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        loadCookieFromDisk();
    }

    /* ------------------------------------------------------------------ */
    /*  Cookie                                                            */
    /* ------------------------------------------------------------------ */

    public String getCookie() {
        return cookie;
    }

    public boolean hasCookie() {
        String c = cookie;
        return c != null && !c.isEmpty();
    }

    public void setCookie(String newCookie) {
        synchronized (cookieLock) {
            this.cookie = newCookie == null ? "" : normalizeCookie(newCookie);
            saveCookieToDisk(this.cookie);
        }
    }

    public void clearCookie() {
        setCookie("");
    }

    private static String normalizeCookie(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        // login/qr/check may return a multi-set-cookie style string joined by ';'
        // Keep MUSIC_U (and friends) but drop Path/HttpOnly/Expires attributes.
        StringBuilder kept = new StringBuilder();
        for (String part : raw.split(";")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            String lower = p.toLowerCase();
            if (lower.startsWith("path=")
                    || lower.startsWith("expires=")
                    || lower.startsWith("max-age=")
                    || lower.startsWith("domain=")
                    || lower.equals("httponly")
                    || lower.equals("secure")
                    || lower.startsWith("samesite=")) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append("; ");
            }
            kept.append(p);
        }
        return kept.toString();
    }

    private void loadCookieFromDisk() {
        try {
            if (COOKIE_FILE.exists()) {
                String text = Files.readString(COOKIE_FILE.toPath(), StandardCharsets.UTF_8).trim();
                if (!text.isEmpty()) {
                    this.cookie = normalizeCookie(text);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void saveCookieToDisk(String value) {
        try {
            File parent = COOKIE_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            if (value == null || value.isEmpty()) {
                if (COOKIE_FILE.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    COOKIE_FILE.delete();
                }
                return;
            }
            Files.writeString(COOKIE_FILE.toPath(), value, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Low-level HTTP                                                    */
    /* ------------------------------------------------------------------ */

    /**
     * Append anti-cache timestamp + randomCNIP for every API call.
     * Docs: foreign / cloud hosts need {@code randomCNIP=true} (or realIP) to avoid 460 cheating.
     */
    private String withCommonParams(String pathAndQuery) {
        String path = pathAndQuery == null ? "" : pathAndQuery;
        StringBuilder sb = new StringBuilder(path);
        char sep = path.contains("?") ? '&' : '?';
        if (!path.contains("timestamp=")) {
            sb.append(sep).append("timestamp=").append(System.currentTimeMillis());
            sep = '&';
        }
        // Always force a fresh CN IP unless the caller already set one explicitly.
        if (!path.contains("randomCNIP=") && !path.contains("realIP=")) {
            sb.append(sep).append("randomCNIP=true");
        }
        return sb.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public JsonObject getJson(String pathAndQuery) throws IOException, InterruptedException {
        return getJson(pathAndQuery, true);
    }

    public JsonObject getJson(String pathAndQuery, boolean withCookie) throws IOException, InterruptedException {
        String url = BASE_URL + withCommonParams(pathAndQuery);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) listclient/1.0")
                .header("Referer", "https://music.163.com/")
                .GET();
        if (withCookie && hasCookie()) {
            builder.header("Cookie", cookie);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        if (body == null || body.isEmpty()) {
            throw new IOException("Empty response from " + pathAndQuery + " (HTTP " + response.statusCode() + ")");
        }
        JsonElement element;
        try {
            element = JsonParser.parseString(body);
        } catch (Exception parseEx) {
            throw new IOException("Invalid JSON from " + pathAndQuery + ": "
                    + body.substring(0, Math.min(160, body.length())), parseEx);
        }
        if (!element.isJsonObject()) {
            throw new IOException("Non-object JSON from " + pathAndQuery + ": "
                    + body.substring(0, Math.min(120, body.length())));
        }
        return element.getAsJsonObject();
    }

    /** Fire-and-forget helper that hops success/failure back onto the MC client thread. */
    public void async(ThrowingSupplier<Void> work, Consumer<Exception> onError) {
        executor.execute(() -> {
            try {
                work.get();
            } catch (Exception ex) {
                if (onError != null) {
                    Minecraft.getInstance().execute(() -> onError.accept(ex));
                } else {
                    ex.printStackTrace();
                }
            }
        });
    }

    public <T> void async(ThrowingSupplier<T> work, Consumer<T> onSuccess, Consumer<Exception> onError) {
        executor.execute(() -> {
            try {
                T result = work.get();
                if (onSuccess != null) {
                    Minecraft.getInstance().execute(() -> onSuccess.accept(result));
                }
            } catch (Exception ex) {
                if (onError != null) {
                    Minecraft.getInstance().execute(() -> onError.accept(ex));
                } else {
                    ex.printStackTrace();
                }
            }
        });
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /* ------------------------------------------------------------------ */
    /*  QR Login                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Stage 1+2 of QR login.
     *
     * @return pair of (dynamic texture id for the QR image, unikey used for polling)
     */
    public Pair<Identifier, String> getLoginQrCode() throws IOException, InterruptedException {
        JsonObject keyJson = getJson("/login/qr/key", false);
        JsonObject keyData = keyJson.getAsJsonObject("data");
        if (keyData == null || !keyData.has("unikey")) {
            throw new IOException("QR key response missing unikey: " + keyJson);
        }
        String uniKey = keyData.get("unikey").getAsString();

        JsonObject createJson = getJson("/login/qr/create?key=" + encode(uniKey) + "&qrimg=true", false);
        JsonObject createData = createJson.getAsJsonObject("data");
        if (createData == null || !createData.has("qrimg")) {
            throw new IOException("QR create response missing qrimg: " + createJson);
        }
        String loginQrCodeBase64 = createData.get("qrimg").getAsString();
        int comma = loginQrCodeBase64.indexOf(',');
        if (comma >= 0) {
            loginQrCodeBase64 = loginQrCodeBase64.substring(comma + 1);
        }

        byte[] bytes = Base64.getDecoder().decode(loginQrCodeBase64);
        // Unique path so re-login refreshes the texture instead of reusing a stale one.
        Identifier identifier = Identifier.fromNamespaceAndPath(
                "listclient",
                "ncm_login_qr_" + (System.currentTimeMillis() % 1_000_000L)
        );
        InputStream stream = new ByteArrayInputStream(bytes);
        // Texture registration must happen on the render thread.
        Minecraft.getInstance().execute(() -> DynamicImageUtils.registerDynamicImage(identifier, stream));
        return new Pair<>(identifier, uniKey);
    }

    /**
     * Stage 3 of QR login. Codes:
     * <ul>
     *   <li>800 – QR expired</li>
     *   <li>801 – waiting for scan</li>
     *   <li>802 – scanned, waiting for confirm</li>
     *   <li>803 – authorized (cookie returned)</li>
     * </ul>
     *
     * @return pair of (code, message or cookie on 803)
     */
    public Pair<Integer, String> qrCodeStateCheck(String loginUniKey) throws IOException, InterruptedException {
        JsonObject json = getJson("/login/qr/check?key=" + encode(loginUniKey) + "&noCookie=true", false);
        int responseCode = json.has("code") ? json.get("code").getAsInt() : -1;
        String responseMessage = json.has("message") && !json.get("message").isJsonNull()
                ? json.get("message").getAsString()
                : "";
        String responseCookie = "";
        if (json.has("cookie") && !json.get("cookie").isJsonNull()) {
            responseCookie = json.get("cookie").getAsString();
        }

        if (responseCode == 803) {
            if (responseCookie == null || responseCookie.isEmpty()) {
                throw new IOException("QR authorized but cookie missing");
            }
            setCookie(responseCookie);
            return new Pair<>(responseCode, cookie);
        }
        return new Pair<>(responseCode, responseMessage);
    }

    public void logout() {
        try {
            if (hasCookie()) {
                getJson("/logout");
            }
        } catch (Exception ignored) {
        } finally {
            clearCookie();
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Account                                                           */
    /* ------------------------------------------------------------------ */

    public NcmUser fetchLoginStatus() throws IOException, InterruptedException {
        if (!hasCookie()) {
            return NcmUser.empty();
        }
        JsonObject json = getJson("/login/status");
        NcmUser user = NcmUser.empty();

        JsonObject data = json.has("data") && json.get("data").isJsonObject()
                ? json.getAsJsonObject("data")
                : json;

        JsonObject profile = null;
        if (data.has("profile") && data.get("profile").isJsonObject()) {
            profile = data.getAsJsonObject("profile");
        } else if (data.has("account") && data.get("account").isJsonObject()) {
            // Some versions nest profile under data.data
        }
        if (profile == null && data.has("data") && data.get("data").isJsonObject()) {
            JsonObject inner = data.getAsJsonObject("data");
            if (inner.has("profile") && inner.get("profile").isJsonObject()) {
                profile = inner.getAsJsonObject("profile");
            }
        }

        if (profile != null) {
            user.loggedIn = true;
            user.userId = optLong(profile, "userId", 0);
            if (user.userId == 0) {
                user.userId = optLong(profile, "id", 0);
            }
            user.nickname = optString(profile, "nickname", "");
            user.avatarUrl = optString(profile, "avatarUrl", "");
            user.signature = optString(profile, "signature", "");
            user.vipType = optInt(profile, "vipType", 0);
        } else {
            // Fall back to /user/account
            return fetchAccount();
        }
        return user;
    }

    public NcmUser fetchAccount() throws IOException, InterruptedException {
        if (!hasCookie()) {
            return NcmUser.empty();
        }
        JsonObject json = getJson("/user/account");
        NcmUser user = NcmUser.empty();
        JsonObject profile = json.has("profile") && json.get("profile").isJsonObject()
                ? json.getAsJsonObject("profile")
                : null;
        JsonObject account = json.has("account") && json.get("account").isJsonObject()
                ? json.getAsJsonObject("account")
                : null;
        if (profile != null) {
            user.loggedIn = true;
            user.userId = optLong(profile, "userId", 0);
            user.nickname = optString(profile, "nickname", "");
            user.avatarUrl = optString(profile, "avatarUrl", "");
            user.signature = optString(profile, "signature", "");
            user.vipType = optInt(profile, "vipType", 0);
        } else if (account != null) {
            user.loggedIn = true;
            user.userId = optLong(account, "id", 0);
            user.nickname = optString(account, "userName", "UID " + user.userId);
        }
        return user;
    }

    public NcmUser fetchUserDetail(long uid) throws IOException, InterruptedException {
        JsonObject json = getJson("/user/detail?uid=" + uid);
        NcmUser user = NcmUser.empty();
        if (json.has("profile") && json.get("profile").isJsonObject()) {
            JsonObject profile = json.getAsJsonObject("profile");
            user.loggedIn = true;
            user.userId = optLong(profile, "userId", uid);
            user.nickname = optString(profile, "nickname", "");
            user.avatarUrl = optString(profile, "avatarUrl", "");
            user.signature = optString(profile, "signature", "");
            user.vipType = optInt(profile, "vipType", 0);
        }
        if (json.has("level")) {
            user.level = optInt(json, "level", 0);
        }
        return user;
    }

    /* ------------------------------------------------------------------ */
    /*  Songs                                                             */
    /* ------------------------------------------------------------------ */

    public String getMusicURL(String musicId) throws IOException, InterruptedException {
        // Prefer v1 endpoint with higher quality when logged in.
        // randomCNIP is injected by getJson/withCommonParams for every call.
        String path = hasCookie()
                ? "/song/url/v1?id=" + encode(musicId) + "&level=exhigh"
                : "/song/url?id=" + encode(musicId);
        JsonObject json = getJson(path);
        if (!json.has("data") || !json.get("data").isJsonArray()) {
            // fallback to legacy
            json = getJson("/song/url?id=" + encode(musicId));
        }
        JsonArray data = json.getAsJsonArray("data");
        if (data == null || data.isEmpty()) {
            return "";
        }
        JsonObject first = data.get(0).getAsJsonObject();
        if (!first.has("url") || first.get("url").isJsonNull()) {
            return "";
        }
        return first.get("url").getAsString();
    }

    public MusicDetail getMusicDetail(String musicId) throws IOException, InterruptedException {
        JsonObject json = getJson("/song/detail?ids=" + encode(musicId));
        return gson.fromJson(json, MusicDetail.class);
    }

    public NcmSong getSong(long id) throws IOException, InterruptedException {
        MusicDetail detail = getMusicDetail(String.valueOf(id));
        if (detail == null || detail.songs == null || detail.songs.isEmpty()) {
            NcmSong empty = new NcmSong();
            empty.id = id;
            empty.name = "未知歌曲";
            return empty;
        }
        return NcmSong.fromDetail(detail.songs.get(0));
    }

    public List<NcmLyricLine> getLyrics(long id) throws IOException, InterruptedException {
        JsonObject json = getJson("/lyric?id=" + id);
        String lrc = "";
        if (json.has("lrc") && json.get("lrc").isJsonObject()) {
            JsonObject lrcObj = json.getAsJsonObject("lrc");
            if (lrcObj.has("lyric") && !lrcObj.get("lyric").isJsonNull()) {
                lrc = lrcObj.get("lyric").getAsString();
            }
        }
        return parseLrc(lrc);
    }

    public static List<NcmLyricLine> parseLrc(String lrc) {
        if (lrc == null || lrc.isEmpty()) {
            return Collections.emptyList();
        }
        List<NcmLyricLine> lines = new ArrayList<>();
        String[] rawLines = lrc.split("\\r?\\n");
        for (String raw : rawLines) {
            Matcher m = LRC_LINE.matcher(raw.trim());
            // A single line can carry multiple timestamps: [00:01.00][00:02.00]text
            List<long[]> stamps = new ArrayList<>();
            int lastEnd = 0;
            Matcher finder = LRC_LINE.matcher(raw.trim());
            String text = "";
            while (finder.find()) {
                int min = Integer.parseInt(finder.group(1));
                int sec = Integer.parseInt(finder.group(2));
                String frac = finder.group(3);
                int ms = 0;
                if (frac != null) {
                    if (frac.length() == 1) ms = Integer.parseInt(frac) * 100;
                    else if (frac.length() == 2) ms = Integer.parseInt(frac) * 10;
                    else ms = Integer.parseInt(frac.substring(0, 3));
                }
                long time = min * 60_000L + sec * 1000L + ms;
                stamps.add(new long[]{time});
                text = finder.group(4) == null ? "" : finder.group(4).trim();
                lastEnd = finder.end();
            }
            if (stamps.isEmpty()) {
                continue;
            }
            // If residual text after last stamp group, prefer it
            if (lastEnd < raw.trim().length() && text.isEmpty()) {
                text = raw.trim().substring(lastEnd).trim();
            }
            if (text.isEmpty()) {
                continue;
            }
            for (long[] stamp : stamps) {
                lines.add(new NcmLyricLine(stamp[0], text));
            }
        }
        lines.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
        return lines;
    }

    /* ------------------------------------------------------------------ */
    /*  Home / Discover                                                   */
    /* ------------------------------------------------------------------ */

    public List<NcmBanner> getBanners() throws IOException, InterruptedException {
        // type=1 => android style banners
        JsonObject json = getJson("/banner?type=1");
        List<NcmBanner> list = new ArrayList<>();
        if (!json.has("banners") || !json.get("banners").isJsonArray()) {
            return list;
        }
        for (JsonElement el : json.getAsJsonArray("banners")) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            NcmBanner b = new NcmBanner();
            b.imageUrl = firstNonEmpty(
                    optString(o, "pic", ""),
                    optString(o, "imageUrl", ""),
                    optString(o, "imgurl", "")
            );
            b.typeTitle = optString(o, "typeTitle", "");
            b.title = firstNonEmpty(optString(o, "title", ""), b.typeTitle);
            b.targetType = optInt(o, "targetType", 0);
            b.targetId = optLong(o, "targetId", 0);
            // song / album / playlist may be nested
            if (b.targetId == 0 && o.has("song") && o.get("song").isJsonObject()) {
                b.targetId = optLong(o.getAsJsonObject("song"), "id", 0);
                b.targetType = 1;
            }
            if (b.targetId == 0 && o.has("targetId") && !o.get("targetId").isJsonNull()) {
                try {
                    b.targetId = o.get("targetId").getAsLong();
                } catch (Exception ignored) {
                }
            }
            b.url = optString(o, "url", "");
            if (!b.imageUrl.isEmpty()) {
                list.add(b);
            }
        }
        return list;
    }

    public List<NcmPlaylist> getPersonalizedPlaylists(int limit) throws IOException, InterruptedException {
        JsonObject json = getJson("/personalized?limit=" + Math.max(1, limit));
        List<NcmPlaylist> list = new ArrayList<>();
        if (!json.has("result") || !json.get("result").isJsonArray()) {
            return list;
        }
        for (JsonElement el : json.getAsJsonArray("result")) {
            if (!el.isJsonObject()) continue;
            list.add(parsePlaylist(el.getAsJsonObject()));
        }
        return list;
    }

    public List<NcmSong> getPersonalizedNewSongs(int limit) throws IOException, InterruptedException {
        JsonObject json = getJson("/personalized/newsong?limit=" + Math.max(1, limit));
        List<NcmSong> list = new ArrayList<>();
        if (!json.has("result") || !json.get("result").isJsonArray()) {
            return list;
        }
        for (JsonElement el : json.getAsJsonArray("result")) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            // newsong wraps the actual song under "song"
            JsonObject songObj = o.has("song") && o.get("song").isJsonObject()
                    ? o.getAsJsonObject("song")
                    : o;
            list.add(parseSongLoose(songObj, o));
        }
        return list;
    }

    public List<NcmSong> getDailyRecommendSongs() throws IOException, InterruptedException {
        if (!hasCookie()) {
            return Collections.emptyList();
        }
        JsonObject json = getJson("/recommend/songs");
        List<NcmSong> list = new ArrayList<>();
        JsonArray arr = null;
        if (json.has("data") && json.get("data").isJsonObject()) {
            JsonObject data = json.getAsJsonObject("data");
            if (data.has("dailySongs") && data.get("dailySongs").isJsonArray()) {
                arr = data.getAsJsonArray("dailySongs");
            }
        }
        if (arr == null && json.has("recommend") && json.get("recommend").isJsonArray()) {
            arr = json.getAsJsonArray("recommend");
        }
        if (arr == null) {
            return list;
        }
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            list.add(parseSongLoose(el.getAsJsonObject(), null));
        }
        return list;
    }

    public List<NcmPlaylist> getDailyRecommendPlaylists() throws IOException, InterruptedException {
        if (!hasCookie()) {
            return Collections.emptyList();
        }
        JsonObject json = getJson("/recommend/resource");
        List<NcmPlaylist> list = new ArrayList<>();
        if (!json.has("recommend") || !json.get("recommend").isJsonArray()) {
            return list;
        }
        for (JsonElement el : json.getAsJsonArray("recommend")) {
            if (!el.isJsonObject()) continue;
            list.add(parsePlaylist(el.getAsJsonObject()));
        }
        return list;
    }

    public List<NcmPlaylist> getUserPlaylists(long uid, int limit) throws IOException, InterruptedException {
        JsonObject json = getJson("/user/playlist?uid=" + uid + "&limit=" + Math.max(1, limit));
        List<NcmPlaylist> list = new ArrayList<>();
        if (!json.has("playlist") || !json.get("playlist").isJsonArray()) {
            return list;
        }
        for (JsonElement el : json.getAsJsonArray("playlist")) {
            if (!el.isJsonObject()) continue;
            list.add(parsePlaylist(el.getAsJsonObject()));
        }
        return list;
    }

    public List<NcmSong> getPlaylistTracks(long playlistId, int limit) throws IOException, InterruptedException {
        return getPlaylistTracks(playlistId, limit, 0);
    }

    public List<NcmSong> getPlaylistTracks(long playlistId, int limit, int offset)
            throws IOException, InterruptedException {
        int lim = Math.max(1, Math.min(limit, 50)); // hard cap – never pull a whole playlist at once
        int off = Math.max(0, offset);
        JsonObject json = getJson("/playlist/track/all?id=" + playlistId
                + "&limit=" + lim + "&offset=" + off);
        List<NcmSong> list = new ArrayList<>();
        if (!json.has("songs") || !json.get("songs").isJsonArray()) {
            return list;
        }
        for (JsonElement el : json.getAsJsonArray("songs")) {
            if (!el.isJsonObject()) continue;
            list.add(parseSongLoose(el.getAsJsonObject(), null));
        }
        return list;
    }

    public List<NcmSong> searchSongs(String keywords, int limit) throws IOException, InterruptedException {
        JsonObject json = getJson("/cloudsearch?keywords=" + encode(keywords) + "&type=1&limit=" + Math.max(1, limit));
        List<NcmSong> list = new ArrayList<>();
        JsonArray songs = null;
        if (json.has("result") && json.get("result").isJsonObject()) {
            JsonObject result = json.getAsJsonObject("result");
            if (result.has("songs") && result.get("songs").isJsonArray()) {
                songs = result.getAsJsonArray("songs");
            }
        }
        if (songs == null) {
            return list;
        }
        for (JsonElement el : songs) {
            if (!el.isJsonObject()) continue;
            list.add(parseSongLoose(el.getAsJsonObject(), null));
        }
        return list;
    }

    public List<NcmSong> getPersonalFm() throws IOException, InterruptedException {
        if (!hasCookie()) {
            return Collections.emptyList();
        }
        JsonObject json = getJson("/personal_fm");
        List<NcmSong> list = new ArrayList<>();
        if (!json.has("data") || !json.get("data").isJsonArray()) {
            return list;
        }
        for (JsonElement el : json.getAsJsonArray("data")) {
            if (!el.isJsonObject()) continue;
            list.add(parseSongLoose(el.getAsJsonObject(), null));
        }
        return list;
    }

    /**
     * Best-effort parse of homepage/block/page – extracts any song-like / playlist-like
     * resources buried in the block tree so the UI has something to show even when the
     * personalized endpoints are empty for guests.
     */
    public HomePageData getHomePage() throws IOException, InterruptedException {
        HomePageData page = new HomePageData();
        // Keep the initial burst tiny. Covers are fetched lazily by the UI for
        // whatever is actually on screen, not for the whole payload.
        try {
            page.banners = getBanners();
            if (page.banners.size() > 5) {
                page.banners = new ArrayList<>(page.banners.subList(0, 5));
            }
        } catch (Exception e) {
            page.banners = new ArrayList<>();
        }
        try {
            page.personalizedPlaylists = getPersonalizedPlaylists(6);
        } catch (Exception e) {
            page.personalizedPlaylists = new ArrayList<>();
        }
        try {
            page.newSongs = getPersonalizedNewSongs(6);
        } catch (Exception e) {
            page.newSongs = new ArrayList<>();
        }
        if (hasCookie()) {
            try {
                List<NcmSong> daily = getDailyRecommendSongs();
                // UI only shows ~5; trim so we don't keep / paint-trigger the rest.
                if (daily.size() > 8) {
                    daily = new ArrayList<>(daily.subList(0, 8));
                }
                page.dailySongs = daily;
            } catch (Exception e) {
                page.dailySongs = new ArrayList<>();
            }
            // Skip daily playlists on first load – personalized cards already fill that slot.
            page.dailyPlaylists = new ArrayList<>();
        }
        return page;
    }

    public static class HomePageData {
        public List<NcmBanner> banners = new ArrayList<>();
        public List<NcmPlaylist> personalizedPlaylists = new ArrayList<>();
        public List<NcmPlaylist> dailyPlaylists = new ArrayList<>();
        public List<NcmSong> dailySongs = new ArrayList<>();
        public List<NcmSong> newSongs = new ArrayList<>();
    }

    /* ------------------------------------------------------------------ */
    /*  Remote image -> dynamic texture                                   */
    /* ------------------------------------------------------------------ */

    public Identifier downloadImage(String url, String textureKey) throws IOException, InterruptedException {
        return downloadImage(url, textureKey, false);
    }

    /**
     * Download, decode, and register a remote image as a dynamic texture.
     * <p>
     * This method is expected to run on a background executor thread (e.g. via
     * {@link #async(ThrowingSupplier, Consumer, Consumer)}). The heavy
     * {@code NativeImage.read()} decoding happens on this thread so the render
     * thread only does a fast {@code TextureManager.register()} call.
     * <p>
     * Returns the {@link Identifier} as soon as registration has been enqueued
     * on the render thread; the texture may not be visible until the next frame.
     *
     * @param circular when true, alpha-masks the image into a circle (avatars).
     */
    public Identifier downloadImage(String url, String textureKey, boolean circular)
            throws IOException, InterruptedException {
        if (url == null || url.isEmpty()) {
            return null;
        }
        String fetchUrl = normalizeImageUrl(url, circular ? 200 : 300);
        byte[] body = fetchImageBytes(fetchUrl);
        // Some CDN hosts reject param= ; retry bare URL once.
        if (body == null || body.length < 32 || looksLikeHtml(body)) {
            String bare = stripImageParams(url);
            if (!bare.equals(fetchUrl)) {
                body = fetchImageBytes(bare);
            }
        }
        if (body == null || body.length < 32 || looksLikeHtml(body)) {
            // http -> https fallback (common on older album art)
            String https = forceHttps(url);
            if (!https.equals(url)) {
                body = fetchImageBytes(normalizeImageUrl(https, circular ? 200 : 300));
                if (body == null || body.length < 32 || looksLikeHtml(body)) {
                    body = fetchImageBytes(stripImageParams(https));
                }
            }
        }
        if (body == null || body.length < 32 || looksLikeHtml(body)) {
            throw new IOException("Image download failed for " + url);
        }

        // Decode on this thread (background executor) — never on the render thread.
        NativeImage decoded;
        try {
            if (circular) {
                decoded = DynamicImageUtils.decodeCircular(body);
            } else {
                decoded = DynamicImageUtils.decodeBytes(body);
            }
        } catch (Exception decodeEx) {
            // Retry once with no Accept preference – the CDN may fall back to JPEG.
            String plainUrl = stripImageParams(url);
            if (!plainUrl.isEmpty() && !plainUrl.equals(fetchUrl)) {
                body = fetchImageBytesPlain(plainUrl);
                if (body == null || body.length < 32 || looksLikeHtml(body)) {
                    throw new IOException("Image bytes undecodable for " + url, decodeEx);
                }
                try {
                    if (circular) {
                        decoded = DynamicImageUtils.decodeCircular(body);
                    } else {
                        decoded = DynamicImageUtils.decodeBytes(body);
                    }
                } catch (Exception ex2) {
                    throw new IOException("Image bytes undecodable for " + url, ex2);
                }
            } else {
                throw new IOException("Image bytes undecodable for " + url, decodeEx);
            }
        }

        Identifier id = Identifier.fromNamespaceAndPath(
                "listclient",
                (circular ? "ncm_img_c_" : "ncm_img_") + sanitizeTextureKey(textureKey)
        );

        // Register on the render thread — this is a fast TextureManager call,
        // no I/O or CPU-heavy decode.
        final NativeImage image = decoded;
        Minecraft.getInstance().execute(() -> {
            DynamicImageUtils.registerImage(id, image);
        });

        return id;
    }

    /** Like fetchImageBytes but sends no Accept header so the CDN picks its default (usually JPEG). */
    private byte[] fetchImageBytesPlain(String fetchUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fetchUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 listclient/1.0")
                    .header("Referer", "https://music.163.com/")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400 || response.body() == null || response.body().length == 0) {
                return null;
            }
            return response.body();
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] fetchImageBytes(String fetchUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fetchUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 listclient/1.0")
                    .header("Referer", "https://music.163.com/")
                    .header("Accept", "image/png,image/jpeg,image/gif,image/bmp,image/*,*/*;q=0.8")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400 || response.body() == null || response.body().length == 0) {
                return null;
            }
            return response.body();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean looksLikeHtml(byte[] body) {
        if (body == null || body.length < 1) return true;
        // Skip BOM / whitespace
        int i = 0;
        while (i < body.length && (body[i] == 0x20 || body[i] == '\n' || body[i] == '\r' || body[i] == '\t')) i++;
        if (i >= body.length) return true;
        // PNG / JPEG / GIF / WEBP magic
        if (body[i] == (byte) 0x89 && i + 3 < body.length && body[i + 1] == 0x50) return false; // PNG
        if (body[i] == (byte) 0xFF && i + 2 < body.length && body[i + 1] == (byte) 0xD8) return false; // JPEG
        if (body[i] == 'G' && i + 2 < body.length && body[i + 1] == 'I') return false; // GIF
        if (body[i] == 'R' && i + 3 < body.length && body[i + 1] == 'I') return false; // RIFF/WEBP
        // HTML / XML error pages
        byte b = body[i];
        return b == '<' || b == '{' || b == '[';
    }

    /** NetEase CDN accepts {@code ?param=WxH}. Preserve existing query if present. */
    private static String normalizeImageUrl(String url, int size) {
        if (url == null || url.isEmpty()) return url;
        String u = url.trim();
        // protocol-relative
        if (u.startsWith("//")) {
            u = "https:" + u;
        }
        // already has param=
        if (u.contains("param=")) {
            return u;
        }
        if (u.contains("?")) {
            return u + "&param=" + size + "y" + size;
        }
        return u + "?param=" + size + "y" + size;
    }

    private static String stripImageParams(String url) {
        if (url == null) return "";
        String u = url.trim();
        if (u.startsWith("//")) u = "https:" + u;
        int q = u.indexOf('?');
        return q >= 0 ? u.substring(0, q) : u;
    }

    private static String forceHttps(String url) {
        if (url == null) return "";
        String u = url.trim();
        if (u.startsWith("//")) return "https:" + u;
        if (u.startsWith("http://")) return "https://" + u.substring(7);
        return u;
    }

    private static String sanitizeTextureKey(String key) {
        if (key == null || key.isEmpty()) {
            return "x" + System.nanoTime();
        }
        StringBuilder sb = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                sb.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append('_');
            }
        }
        // Identifier path must be [a-z0-9/._-]
        return sb.toString();
    }

    /* ------------------------------------------------------------------ */
    /*  JSON helpers                                                      */
    /* ------------------------------------------------------------------ */

    private NcmPlaylist parsePlaylist(JsonObject o) {
        NcmPlaylist p = new NcmPlaylist();
        p.id = optLong(o, "id", 0);
        p.name = optString(o, "name", "");
        p.coverUrl = firstNonEmpty(
                optString(o, "picUrl", ""),
                optString(o, "coverImgUrl", ""),
                optString(o, "picUrl", "")
        );
        p.description = optString(o, "copywriter", optString(o, "description", ""));
        p.playCount = optLong(o, "playCount", optLong(o, "playcount", 0));
        p.trackCount = optInt(o, "trackCount", optInt(o, "trackNumberUpdateTime", 0));
        if (o.has("creator") && o.get("creator").isJsonObject()) {
            p.creatorName = optString(o.getAsJsonObject("creator"), "nickname", "");
        }
        p.subscribed = o.has("subscribed") && !o.get("subscribed").isJsonNull() && o.get("subscribed").getAsBoolean();
        p.specialTypeLiked = optInt(o, "specialType", 0) == 5;
        return p;
    }

    private NcmSong parseSongLoose(JsonObject songObj, JsonObject wrapper) {
        NcmSong s = new NcmSong();
        s.id = optLong(songObj, "id", wrapper != null ? optLong(wrapper, "id", 0) : 0);
        s.name = firstNonEmpty(optString(songObj, "name", ""), wrapper != null ? optString(wrapper, "name", "") : "");
        s.durationMs = optInt(songObj, "dt", optInt(songObj, "duration", 0));

        // artists: ar[] (v3) or artists[] (v2)
        if (songObj.has("ar") && songObj.get("ar").isJsonArray()) {
            s.artists = joinNames(songObj.getAsJsonArray("ar"));
        } else if (songObj.has("artists") && songObj.get("artists").isJsonArray()) {
            s.artists = joinNames(songObj.getAsJsonArray("artists"));
        } else if (wrapper != null && wrapper.has("artist") && wrapper.get("artist").isJsonObject()) {
            s.artists = optString(wrapper.getAsJsonObject("artist"), "name", "");
        }

        // album
        JsonObject al = null;
        if (songObj.has("al") && songObj.get("al").isJsonObject()) {
            al = songObj.getAsJsonObject("al");
        } else if (songObj.has("album") && songObj.get("album").isJsonObject()) {
            al = songObj.getAsJsonObject("album");
        }
        if (al != null) {
            s.album = optString(al, "name", "");
            s.coverUrl = firstNonEmpty(optString(al, "picUrl", ""), optString(al, "blurPicUrl", ""));
        }
        if (s.coverUrl.isEmpty() && wrapper != null) {
            s.coverUrl = firstNonEmpty(optString(wrapper, "picUrl", ""), optString(wrapper, "coverImgUrl", ""));
        }
        return s;
    }

    private static String joinNames(JsonArray arr) {
        StringBuilder sb = new StringBuilder();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            String name = optString(el.getAsJsonObject(), "name", "");
            if (name.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" / ");
            sb.append(name);
        }
        return sb.toString();
    }

    private static String optString(JsonObject o, String key, String def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        try {
            return o.get(key).getAsString();
        } catch (Exception e) {
            return def;
        }
    }

    private static int optInt(JsonObject o, String key, int def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        try {
            return o.get(key).getAsInt();
        } catch (Exception e) {
            return def;
        }
    }

    private static long optLong(JsonObject o, String key, long def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        try {
            return o.get(key).getAsLong();
        } catch (Exception e) {
            return def;
        }
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.isEmpty()) return v;
        }
        return "";
    }
}
