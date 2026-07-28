package dev.naominet.listclient.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.net.URI;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Microsoft account login through the legacy List client's browser OAuth flow:
 * <ol>
 *   <li>open the Microsoft authorization page and receive its local callback,</li>
 *   <li>exchange the authorization code for a Microsoft access/refresh token,</li>
 *   <li>exchange the access token through Xbox Live &rarr; XSTS &rarr;
 *       Minecraft services,</li>
 *   <li>fetch the Minecraft profile (uuid + name).</li>
 * </ol>
 * All network work runs on a daemon worker thread; every callback is
 * marshalled back to the Minecraft render thread via
 * {@link Minecraft#execute(Runnable)}. Errors are reported as strings through
 * the error callback – nothing is thrown to the caller.
 */
public final class MicrosoftAuth {

    /** OAuth application used by the legacy List client browser flow. */
    private static final String CLIENT_ID = "288ec5dd-6736-4d4b-9b96-30e083a8cad2";
    private static final int REDIRECT_PORT = 29116;
    private static final String REDIRECT_URI = "http://localhost:" + REDIRECT_PORT + "/authentication-response";
    // Existing refresh tokens were issued with this legacy desktop redirect URI.
    private static final String REFRESH_REDIRECT_URI = "https://login.live.com/oauth20_desktop.srf";

    private static final String AUTHORIZE_URL = "https://login.live.com/oauth20_authorize.srf";
    private static final String TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Browser handoff state. The legacy loopback flow has no user code. */
    public record DeviceCode(String userCode, String verificationUri) {
    }

    /** Successful login: Minecraft profile + tokens. */
    public record Result(String uuid, String name, String mcAccessToken, String msRefreshToken) {
    }

    private volatile boolean cancelled;
    private volatile HttpServer callbackServer;

    /**
     * Starts the legacy browser OAuth flow on a background thread. The listener
     * is ready before the authorization page is opened.
     *
     * @param onCode    receives browser handoff state (render thread)
     * @param onSuccess receives the finished login (render thread)
     * @param onError   receives a human-readable error string (render thread)
     */
    public void start(Consumer<DeviceCode> onCode, Consumer<Result> onSuccess, Consumer<String> onError) {
        startWorker(() -> run(onCode, onSuccess, onError));
    }

    /**
     * Restores a Microsoft account from its persisted OAuth refresh token.
     * This ports the legacy SessionUtils refresh-token backend, but keeps all
     * networking off the render thread and returns a fresh Minecraft token.
     */
    public void startRefresh(String refreshToken, Consumer<Result> onSuccess, Consumer<String> onError) {
        if (refreshToken == null || refreshToken.isBlank()) {
            fail(onError, "missing refresh token");
            return;
        }
        startWorker(() -> runRefresh(refreshToken, onSuccess, onError));
    }

    /** Imports an already-issued Minecraft services access token. */
    public void startMinecraftToken(String accessToken, Consumer<Result> onSuccess, Consumer<String> onError) {
        if (accessToken == null || accessToken.isBlank()) {
            fail(onError, "missing access token");
            return;
        }
        startWorker(() -> runMinecraftToken(accessToken, onSuccess, onError));
    }

    private void startWorker(Runnable work) {
        cancelled = false;
        Thread t = new Thread(work, "ms-auth");
        t.setDaemon(true);
        t.start();
    }

    /** Stops the poll loop; no further callbacks are delivered. */
    public void cancel() {
        cancelled = true;
        HttpServer server = callbackServer;
        if (server != null) {
            server.stop(0);
            callbackServer = null;
        }
    }

    /* ================================================================== */
    /*  flow                                                              */
    /* ================================================================== */

    private void runRefresh(String refreshToken, Consumer<Result> onSuccess, Consumer<String> onError) {
        try {
            JsonObject token;
            try {
                token = requestRefreshToken(refreshToken, REDIRECT_URI);
            } catch (IllegalStateException firstFailure) {
                // Stored accounts from the legacy client were authorized against
                // the desktop URI. Preserve their ability to refresh without
                // weakening the browser callback used by new accounts.
                token = requestRefreshToken(refreshToken, REFRESH_REDIRECT_URI);
            }
            String msAccessToken = required(token, "access_token", "Microsoft refresh response");
            String nextRefreshToken = token.has("refresh_token")
                    ? token.get("refresh_token").getAsString() : refreshToken;
            completeXboxLogin(msAccessToken, nextRefreshToken, onSuccess, onError);
        } catch (Exception ex) {
            failException(onError, ex);
        }
    }

    private static JsonObject requestRefreshToken(String refreshToken, String redirectUri) throws Exception {
        return postForm(TOKEN_URL,
                "client_id=" + enc(CLIENT_ID)
                        + "&refresh_token=" + enc(refreshToken)
                        + "&grant_type=refresh_token"
                        + "&redirect_uri=" + enc(redirectUri)
                        + "&scope=" + enc("XboxLive.signin offline_access"));
    }

    private void runMinecraftToken(String accessToken, Consumer<Result> onSuccess, Consumer<String> onError) {
        try {
            Profile profile = fetchProfile(accessToken);
            if (cancelled) return;
            onRenderThread(() -> onSuccess.accept(
                    new Result(profile.uuid(), profile.name(), accessToken, "")));
        } catch (Exception ex) {
            failException(onError, ex);
        }
    }

    private void completeXboxLogin(String msAccessToken, String msRefreshToken,
                                   Consumer<Result> onSuccess, Consumer<String> onError) throws Exception {
        if (cancelled) return;

        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProps.addProperty("RpsTicket", "d=" + msAccessToken);
        JsonObject xblBody = new JsonObject();
        xblBody.add("Properties", xblProps);
        xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblBody.addProperty("TokenType", "JWT");
        JsonObject xbl = postJson(XBL_URL, xblBody);
        String xblToken = required(xbl, "Token", "Xbox Live response");
        String uhs = xbl.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject()
                .get("uhs").getAsString();

        if (cancelled) return;

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
        JsonObject xsts = parseObject(xstsResp.body(), "XSTS response");
        if (xstsResp.statusCode() != 200) {
            throw new IllegalStateException(xstsErrorText(xsts, xstsResp.statusCode()));
        }
        String xstsToken = required(xsts, "Token", "XSTS response");

        if (cancelled) return;

        JsonObject mcBody = new JsonObject();
        mcBody.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        JsonObject mc = postJson(MC_LOGIN_URL, mcBody);
        String mcAccessToken = required(mc, "access_token", "Minecraft login response");
        Profile profile = fetchProfile(mcAccessToken);

        if (cancelled) return;
        onRenderThread(() -> onSuccess.accept(new Result(
                profile.uuid(), profile.name(), mcAccessToken, msRefreshToken)));
    }

    private Profile fetchProfile(String accessToken) throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(MC_PROFILE_URL))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build());
        if (response.statusCode() == 404) {
            throw new IllegalStateException("this account owns no Minecraft profile");
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("profile HTTP " + response.statusCode());
        }
        JsonObject profile = parseObject(response.body(), "Minecraft profile");
        return new Profile(required(profile, "id", "Minecraft profile"),
                required(profile, "name", "Minecraft profile"));
    }

    private record Profile(String uuid, String name) {
    }

    private void run(Consumer<DeviceCode> onCode, Consumer<Result> onSuccess, Consumer<String> onError) {
        HttpServer server = null;
        try {
            CountDownLatch callback = new CountDownLatch(1);
            String[] codeHolder = new String[1];
            String[] errorHolder = new String[1];
            String state = UUID.randomUUID().toString();
            server = HttpServer.create(new InetSocketAddress(REDIRECT_PORT), 0);
            callbackServer = server;
            server.createContext("/authentication-response", exchange -> {
                String responseText = "Login received. You may close this page.";
                int responseStatus = 200;
                try {
                    Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                    if (!state.equals(query.get("state"))) {
                        errorHolder[0] = "Microsoft login state did not match. Please try again.";
                        responseText = "Login could not be verified. Return to Minecraft and try again.";
                        responseStatus = 400;
                    } else {
                        codeHolder[0] = query.get("code");
                        errorHolder[0] = query.get("error_description");
                        if (codeHolder[0] == null && errorHolder[0] == null) {
                            errorHolder[0] = "authorization response contained no code";
                        }
                    }
                } finally {
                    byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                    exchange.sendResponseHeaders(responseStatus, bytes.length);
                    try (var body = exchange.getResponseBody()) {
                        body.write(bytes);
                    }
                    callback.countDown();
                }
            });
            server.start();

            String authorizationUri = AUTHORIZE_URL
                    + "?client_id=" + enc(CLIENT_ID)
                    + "&response_type=code"
                    + "&redirect_uri=" + enc(REDIRECT_URI)
                    + "&scope=" + enc("XboxLive.signin offline_access")
                    + "&state=" + enc(state);
            onRenderThread(() -> {
                onCode.accept(new DeviceCode("", authorizationUri));
                try {
                    Util.getPlatform().openUri(authorizationUri);
                } catch (Exception ignored) {
                }
            });

            while (!cancelled && !callback.await(1, TimeUnit.SECONDS)) {
            }
            if (cancelled) return;
            if (errorHolder[0] != null) {
                throw new IllegalStateException(errorHolder[0]);
            }
            String authorizationCode = codeHolder[0];
            if (authorizationCode == null || authorizationCode.isBlank()) {
                throw new IllegalStateException("authorization response contained no code");
            }

            JsonObject token = postForm(TOKEN_URL,
                    "client_id=" + enc(CLIENT_ID)
                            + "&code=" + enc(authorizationCode)
                            + "&grant_type=authorization_code"
                            + "&redirect_uri=" + enc(REDIRECT_URI));
            String msAccessToken = required(token, "access_token", "Microsoft token response");
            String refreshToken = token.has("refresh_token")
                    ? token.get("refresh_token").getAsString() : "";
            completeXboxLogin(msAccessToken, refreshToken, onSuccess, onError);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            failException(onError, ex);
        } finally {
            if (server != null) server.stop(0);
            callbackServer = null;
        }
    }

    /* ================================================================== */
    /*  helpers                                                           */
    /* ================================================================== */

    private void failException(Consumer<String> onError, Exception ex) {
        String message = ex.getMessage();
        fail(onError, message == null || message.isBlank()
                ? ex.getClass().getSimpleName() : message);
    }

    private static JsonObject parseObject(String body, String context) {
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception ex) {
            throw new IllegalStateException(context + " returned invalid JSON");
        }
    }

    private static String required(JsonObject object, String key, String context) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            throw new IllegalStateException(context + " is missing " + key);
        }
        return object.get(key).getAsString();
    }

    private void fail(Consumer<String> onError, String message) {
        if (cancelled) return;
        onRenderThread(() -> onError.accept(message));
    }

    private static void onRenderThread(Runnable r) {
        Minecraft.getInstance().execute(r);
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return values;
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            String key = separator < 0 ? pair : pair.substring(0, separator);
            String value = separator < 0 ? "" : pair.substring(separator + 1);
            values.put(java.net.URLDecoder.decode(key, StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return values;
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
