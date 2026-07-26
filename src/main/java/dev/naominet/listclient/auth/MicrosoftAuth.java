package dev.naominet.listclient.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Microsoft account login via the standard OAuth 2.0 device-code flow
 * (HMCL/launcher style):
 * <ol>
 *   <li>request a device code + user code from login.microsoftonline.com,</li>
 *   <li>poll the token endpoint while the user enters the code in a browser,</li>
 *   <li>exchange the MS access token through Xbox Live &rarr; XSTS &rarr;
 *       Minecraft services,</li>
 *   <li>fetch the Minecraft profile (uuid + name).</li>
 * </ol>
 * All network work runs on a daemon worker thread; every callback is
 * marshalled back to the Minecraft render thread via
 * {@link Minecraft#execute(Runnable)}. Errors are reported as strings through
 * the error callback – nothing is thrown to the caller.
 */
public final class MicrosoftAuth {

    /** Public client id (the well-known Minecraft/Xbox one used by many open-source launchers); replace if you have your own Azure app. */
    private static final String CLIENT_ID = "00000000402b5328";

    private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Code the user must enter in the browser, plus where to enter it. */
    public record DeviceCode(String userCode, String verificationUri) {
    }

    /** Successful login: Minecraft profile + tokens. */
    public record Result(String uuid, String name, String mcAccessToken, String msRefreshToken) {
    }

    private volatile boolean cancelled;

    /**
     * Starts the device-code flow on a background thread and auto-opens the
     * verification page in the system browser once the user code is known.
     *
     * @param onCode    receives the user code to display (render thread)
     * @param onSuccess receives the finished login (render thread)
     * @param onError   receives a human-readable error string (render thread)
     */
    public void start(Consumer<DeviceCode> onCode, Consumer<Result> onSuccess, Consumer<String> onError) {
        Thread t = new Thread(() -> run(onCode, onSuccess, onError), "ms-auth");
        t.setDaemon(true);
        t.start();
    }

    /** Stops the poll loop; no further callbacks are delivered. */
    public void cancel() {
        cancelled = true;
    }

    /* ================================================================== */
    /*  flow                                                              */
    /* ================================================================== */

    private void run(Consumer<DeviceCode> onCode, Consumer<Result> onSuccess, Consumer<String> onError) {
        try {
            // A: device code
            JsonObject dc = postForm(DEVICE_CODE_URL,
                    "client_id=" + CLIENT_ID + "&scope=" + enc("XboxLive.signin offline_access"));
            String deviceCode = dc.get("device_code").getAsString();
            String userCode = dc.get("user_code").getAsString();
            String verificationUri = dc.get("verification_uri").getAsString();
            long intervalMs = (dc.has("interval") ? dc.get("interval").getAsLong() : 5L) * 1000L;
            long deadline = System.currentTimeMillis()
                    + (dc.has("expires_in") ? dc.get("expires_in").getAsLong() : 900L) * 1000L;

            onRenderThread(() -> {
                onCode.accept(new DeviceCode(userCode, verificationUri));
                try {
                    Util.getPlatform().openUri(verificationUri);
                } catch (Exception ignored) {
                }
            });

            // B: poll token endpoint
            String msAccessToken = null;
            String msRefreshToken = "";
            while (msAccessToken == null) {
                if (cancelled) return;
                if (System.currentTimeMillis() > deadline) {
                    fail(onError, "device code expired");
                    return;
                }
                Thread.sleep(intervalMs);
                if (cancelled) return;

                HttpResponse<String> resp = send(HttpRequest.newBuilder(URI.create(TOKEN_URL))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code")
                                        + "&client_id=" + CLIENT_ID
                                        + "&device_code=" + enc(deviceCode)))
                        .build());
                JsonObject tok = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (tok.has("access_token")) {
                    msAccessToken = tok.get("access_token").getAsString();
                    if (tok.has("refresh_token")) {
                        msRefreshToken = tok.get("refresh_token").getAsString();
                    }
                    break;
                }
                String err = tok.has("error") ? tok.get("error").getAsString() : "unknown";
                switch (err) {
                    case "authorization_pending" -> {
                    }
                    case "slow_down" -> intervalMs += 5000L;
                    case "expired_token" -> {
                        fail(onError, "device code expired");
                        return;
                    }
                    case "authorization_declined", "access_denied" -> {
                        fail(onError, "authorization declined");
                        return;
                    }
                    default -> {
                        fail(onError, "token error: " + err);
                        return;
                    }
                }
            }

            // C: Xbox Live
            JsonObject xblProps = new JsonObject();
            xblProps.addProperty("AuthMethod", "RPS");
            xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
            xblProps.addProperty("RpsTicket", "d=" + msAccessToken);
            JsonObject xblBody = new JsonObject();
            xblBody.add("Properties", xblProps);
            xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
            xblBody.addProperty("TokenType", "JWT");
            JsonObject xbl = postJson(XBL_URL, xblBody);
            String xblToken = xbl.get("Token").getAsString();
            String uhs = xbl.getAsJsonObject("DisplayClaims")
                    .getAsJsonArray("xui").get(0).getAsJsonObject()
                    .get("uhs").getAsString();

            if (cancelled) return;

            // D: XSTS
            JsonObject xstsProps = new JsonObject();
            xstsProps.addProperty("SandboxId", "RETAIL");
            JsonArray userTokens = new JsonArray();
            userTokens.add(xblToken);
            xstsProps.add("UserTokens", userTokens);
            JsonObject xstsBody = new JsonObject();
            xstsBody.add("Properties", xstsProps);
            xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
            xstsBody.addProperty("TokenType", "JWT");

            HttpResponse<String> xstsResp = send(HttpRequest.newBuilder(URI.create(XSTS_URL))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(xstsBody.toString()))
                    .build());
            JsonObject xsts = JsonParser.parseString(xstsResp.body()).getAsJsonObject();
            if (xstsResp.statusCode() != 200) {
                fail(onError, xstsErrorText(xsts, xstsResp.statusCode()));
                return;
            }
            String xstsToken = xsts.get("Token").getAsString();

            if (cancelled) return;

            // E: Minecraft services
            JsonObject mcBody = new JsonObject();
            mcBody.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
            JsonObject mc = postJson(MC_LOGIN_URL, mcBody);
            String mcAccessToken = mc.get("access_token").getAsString();

            if (cancelled) return;

            // F: profile
            HttpResponse<String> profResp = send(HttpRequest.newBuilder(URI.create(MC_PROFILE_URL))
                    .header("Authorization", "Bearer " + mcAccessToken)
                    .GET()
                    .build());
            if (profResp.statusCode() == 404) {
                fail(onError, "this account owns no Minecraft profile");
                return;
            }
            if (profResp.statusCode() != 200) {
                fail(onError, "profile HTTP " + profResp.statusCode());
                return;
            }
            JsonObject profile = JsonParser.parseString(profResp.body()).getAsJsonObject();
            String uuid = profile.get("id").getAsString();
            String name = profile.get("name").getAsString();

            if (cancelled) return;
            String refresh = msRefreshToken;
            onRenderThread(() -> onSuccess.accept(new Result(uuid, name, mcAccessToken, refresh)));
        } catch (InterruptedException ignored) {
        } catch (Exception ex) {
            String msg = ex.getMessage();
            fail(onError, msg == null || msg.isEmpty() ? ex.getClass().getSimpleName() : msg);
        }
    }

    /* ================================================================== */
    /*  helpers                                                           */
    /* ================================================================== */

    /** Copies text to the system clipboard (render thread only). */
    public static void copyToClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    private void fail(Consumer<String> onError, String message) {
        if (cancelled) return;
        onRenderThread(() -> onError.accept(message));
    }

    private static void onRenderThread(Runnable r) {
        Minecraft.getInstance().execute(r);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** POSTs a form body and parses the JSON response, failing on non-2xx. */
    private static JsonObject postForm(String url, String body) throws Exception {
        HttpResponse<String> resp = send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException(shortHttpError(url, resp));
        }
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    /** POSTs a JSON body and parses the JSON response, failing on non-2xx. */
    private static JsonObject postJson(String url, JsonObject body) throws Exception {
        HttpResponse<String> resp = send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException(shortHttpError(url, resp));
        }
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    private static String shortHttpError(String url, HttpResponse<String> resp) {
        String host = URI.create(url).getHost();
        return host + " HTTP " + resp.statusCode();
    }

    /** Maps well-known XSTS XErr codes to something a user can act on. */
    private static String xstsErrorText(JsonObject body, int status) {
        long xerr = body != null && body.has("XErr") ? body.get("XErr").getAsLong() : 0L;
        String known = switch ((int) (xerr % 100000L)) {
            case 16233 -> "no Xbox account (create one at xbox.com)";   // 2148916233
            case 16235 -> "Xbox Live unavailable in this region";       // 2148916235
            case 16236, 16237, 16238 -> "child account (needs family)"; // 2148916236-8
            default -> null;
        };
        if (known != null) return known;
        return String.format(Locale.ROOT, "XSTS HTTP %d%s", status, xerr != 0 ? " XErr " + xerr : "");
    }
}
