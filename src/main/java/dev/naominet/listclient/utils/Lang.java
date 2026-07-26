package dev.naominet.listclient.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Client-side i18n, fully independent of Minecraft's language setting.
 * <p>
 * Translations live in {@code assets/listclient/lang/<code>.json} (flat
 * key→string maps); the selected language persists in {@code List/lang.txt}
 * next to the other client files. Missing keys fall back to en_us, then to
 * the key itself so untranslated UI stays readable. First use decides the
 * default from the system locale (Chinese → zh_cn, else en_us).
 * <p>
 * {@link #cycle()} switches to the next bundled language and saves – UI can
 * expose it as a single toggle button.
 */
public final class Lang {

    public static final String[] LANGUAGES = {"zh_cn", "en_us"};

    private static final Path SAVE = Path.of("List", "lang.txt");

    private static Map<String, String> table;
    private static Map<String, String> fallback;
    private static String current;

    /** Translates a key; falls back to en_us, then to the key itself. */
    public static String tr(String key) {
        ensureLoaded();
        String v = table.get(key);
        if (v != null) return v;
        v = fallback.get(key);
        return v != null ? v : key;
    }

    /** Translates and {@link String#format formats}. */
    public static String tr(String key, Object... args) {
        try {
            return String.format(tr(key), args);
        } catch (Exception e) {
            return tr(key);
        }
    }

    public static String current() {
        ensureLoaded();
        return current;
    }

    /** Display name of the language that {@link #cycle()} would switch to. */
    public static String nextName() {
        ensureLoaded();
        return "zh_cn".equals(current) ? "English" : "中文";
    }

    /** Switches to the next bundled language and persists the choice. */
    public static void cycle() {
        ensureLoaded();
        int i = 0;
        for (int k = 0; k < LANGUAGES.length; k++) {
            if (LANGUAGES[k].equals(current)) i = k;
        }
        set(LANGUAGES[(i + 1) % LANGUAGES.length]);
    }

    public static void set(String code) {
        ensureLoaded();
        current = code;
        table = read(code);
        try {
            Files.createDirectories(SAVE.getParent());
            Files.writeString(SAVE, code, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static synchronized void ensureLoaded() {
        if (table != null) return;
        fallback = read("en_us");
        String saved = null;
        try {
            if (Files.isRegularFile(SAVE)) {
                saved = Files.readString(SAVE, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception ignored) {
        }
        if (saved == null || saved.isEmpty() || !isBundled(saved)) {
            saved = Locale.getDefault().getLanguage().equalsIgnoreCase("zh") ? "zh_cn" : "en_us";
        }
        current = saved;
        table = read(saved);
    }

    private static boolean isBundled(String code) {
        for (String l : LANGUAGES) {
            if (l.equals(code)) return true;
        }
        return false;
    }

    private static Map<String, String> read(String code) {
        Map<String, String> out = new HashMap<>();
        try (InputStream in = Lang.class.getResourceAsStream("/assets/listclient/lang/" + code + ".json")) {
            if (in == null) return out;
            JsonObject obj = JsonParser
                    .parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                out.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private Lang() {
    }
}
