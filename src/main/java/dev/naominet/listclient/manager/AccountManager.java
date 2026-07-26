package dev.naominet.listclient.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Account manager: keeps typed account records (offline names and Microsoft
 * logins) in {@code List/accounts.json} and hot-swaps the running session.
 * <p>
 * Switching replaces {@code Minecraft.user} with a new {@link User} – an
 * offline one (offline-player UUID, empty token) or a Microsoft one carrying
 * the real Minecraft services access token – and resets {@code profileFuture}
 * to a completed-null future so {@code getGameProfile()} rebuilds the profile
 * from the new user on every call; both fields are opened by the access
 * widener.
 * <p>
 * The legacy on-disk format ({@code {"accounts":["name", ...]}}) is migrated
 * on load: each name becomes an OFFLINE record with the computed offline UUID.
 */
public final class AccountManager {

    public static final AccountManager instance = new AccountManager();

    private static final Path FILE = Path.of("List", "accounts.json");

    /** Account kind – decides how {@link #switchTo(Account)} builds the session. */
    public enum Type {
        OFFLINE, MICROSOFT
    }

    /**
     * One stored account. {@code uuid} may be dashless (as returned by the
     * Minecraft profile endpoint) or dashed; {@code mcToken}/{@code msRefresh}
     * are empty strings for OFFLINE accounts.
     */
    public record Account(String name, String uuid, Type type, String mcToken, String msRefresh) {

        public boolean microsoft() {
            return type == Type.MICROSOFT;
        }

        public UUID uuidObject() {
            return parseUuid(uuid, name);
        }
    }

    private final List<Account> accounts = new ArrayList<>();
    private boolean loaded;

    public List<Account> getAccounts() {
        ensureLoaded();
        return accounts;
    }

    public String currentName() {
        User u = Minecraft.getInstance().getUser();
        return u == null ? "" : u.getName();
    }

    /** Valid Minecraft offline name: 3-16 word characters. */
    public boolean isValidName(String name) {
        return name != null && name.matches("\\w{3,16}");
    }

    /** Adds an offline account; false if the name is invalid or already listed. */
    public boolean addOffline(String name) {
        ensureLoaded();
        if (!isValidName(name)) {
            return false;
        }
        for (Account a : accounts) {
            if (a.type() == Type.OFFLINE && a.name().equals(name)) {
                return false;
            }
        }
        accounts.add(new Account(name, offlineUuid(name).toString(), Type.OFFLINE, "", ""));
        save();
        return true;
    }

    /**
     * Adds (or refreshes) a Microsoft account after a completed device-code
     * login, saves, and immediately switches the session to it.
     */
    public void addMicrosoft(String uuid, String name, String mcToken, String msRefresh) {
        ensureLoaded();
        accounts.removeIf(a -> a.type() == Type.MICROSOFT && sameUuid(a.uuid(), uuid));
        Account account = new Account(name, uuid, Type.MICROSOFT,
                mcToken == null ? "" : mcToken, msRefresh == null ? "" : msRefresh);
        accounts.add(account);
        save();
        switchTo(account);
    }

    public void remove(Account account) {
        ensureLoaded();
        accounts.remove(account);
        save();
    }

    /** Hot-swaps the running session to this account. */
    public void switchTo(Account account) {
        if (account == null) return;
        Minecraft mc = Minecraft.getInstance();
        User user = account.microsoft()
                ? new User(account.name(), account.uuidObject(), account.mcToken(),
                        Optional.empty(), Optional.empty())
                : new User(account.name(), offlineUuid(account.name()), "",
                        Optional.empty(), Optional.empty());
        mc.user = user;
        mc.profileFuture = CompletableFuture.completedFuture(null);
        mc.updateTitle();
    }

    /* ================================================================== */
    /*  uuid helpers                                                      */
    /* ================================================================== */

    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    /** Accepts dashed or dashless UUIDs; falls back to the offline UUID. */
    private static UUID parseUuid(String raw, String fallbackName) {
        try {
            String s = raw.replace("-", "");
            if (s.length() == 32) {
                return UUID.fromString(s.replaceFirst(
                        "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
            }
        } catch (Exception ignored) {
        }
        return offlineUuid(fallbackName);
    }

    private static boolean sameUuid(String a, String b) {
        return a != null && b != null && a.replace("-", "").equalsIgnoreCase(b.replace("-", ""));
    }

    /* ================================================================== */
    /*  persistence                                                       */
    /* ================================================================== */

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.isRegularFile(FILE)) return;
            JsonElement root = JsonParser
                    .parseString(Files.readString(FILE, StandardCharsets.UTF_8));
            if (root.isJsonObject() && root.getAsJsonObject().has("accounts")) {
                // Legacy format: {"accounts":["name", ...]} of offline names.
                for (JsonElement e : root.getAsJsonObject().getAsJsonArray("accounts")) {
                    String n = e.getAsString();
                    if (isValidName(n)) {
                        accounts.add(new Account(n, offlineUuid(n).toString(), Type.OFFLINE, "", ""));
                    }
                }
                save(); // rewrite in the new format right away
                return;
            }
            if (!root.isJsonArray()) return;
            for (JsonElement e : root.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                String name = o.has("name") ? o.get("name").getAsString() : "";
                if (name.isEmpty()) continue;
                Type type = Type.OFFLINE;
                try {
                    type = Type.valueOf(o.has("type") ? o.get("type").getAsString() : "OFFLINE");
                } catch (IllegalArgumentException ignored) {
                }
                String uuid = o.has("uuid") ? o.get("uuid").getAsString()
                        : offlineUuid(name).toString();
                String mcToken = o.has("mcToken") ? o.get("mcToken").getAsString() : "";
                String msRefresh = o.has("msRefresh") ? o.get("msRefresh").getAsString() : "";
                accounts.add(new Account(name, uuid, type, mcToken, msRefresh));
            }
        } catch (Exception ignored) {
        }
    }

    private void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonArray arr = new JsonArray();
            for (Account a : accounts) {
                JsonObject o = new JsonObject();
                o.addProperty("name", a.name());
                o.addProperty("uuid", a.uuid());
                o.addProperty("type", a.type().name());
                o.addProperty("mcToken", a.mcToken());
                o.addProperty("msRefresh", a.msRefresh());
                arr.add(o);
            }
            Files.writeString(FILE, arr.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private AccountManager() {
    }
}
