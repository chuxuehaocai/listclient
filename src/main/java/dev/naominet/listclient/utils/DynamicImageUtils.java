package dev.naominet.listclient.utils;

import com.mojang.blaze3d.platform.NativeImage;
import dev.naominet.listclient.core.ListClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.imageio.ImageIO;

public class DynamicImageUtils {
    private static final CopyOnWriteArrayList<Identifier> textureList = new CopyOnWriteArrayList<>();

    public static void registerDynamicImageFromResources(Identifier identifier, String path) {
        try (InputStream stream = ListClient.class.getResourceAsStream("/assets/listclient/textures/" + path)) {
            if (stream == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            registerDynamicImage(identifier, stream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void registerDynamicImageFromResources(Identifier identifier) {
        if (textureList.stream().noneMatch(id -> id.getPath().equals(identifier.getPath()))) {
            try (InputStream stream = ListClient.class.getResourceAsStream(
                    "/assets/" + identifier.getNamespace() + "/" + identifier.getPath())) {
                if (stream == null) {
                    throw new IllegalArgumentException("Resource not found: " + "/assets/"
                            + identifier.getNamespace() + "/" + identifier.getPath());
                }
                registerDynamicImage(identifier, stream);
                textureList.add(identifier);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static Identifier registerAsDynamicImageFromClientResources(String path, String textureId) {
        String dynamicId = "dynamic-texture-" + textureId;
        if (textureList.stream().noneMatch(identifier -> identifier.getPath().equals(dynamicId))) {
            Identifier identifier = Identifier.fromNamespaceAndPath("listclient", dynamicId);
            registerDynamicImageFromResources(identifier, path);
            textureList.add(identifier);
            return identifier;
        } else {
            return textureList.stream()
                    .filter(identifier -> identifier.getPath().equals(dynamicId))
                    .toList()
                    .getFirst();
        }
    }

    public static void registerDynamicImage(Identifier identifier, InputStream stream) {
        try {
            NativeImage image = NativeImage.read(stream);
            registerDynamicImage(identifier, image);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void registerDynamicImage(Identifier identifier, byte[] pngOrJpegBytes) {
        try {
            NativeImage image = NativeImage.read(ensurePng(pngOrJpegBytes));
            registerDynamicImage(identifier, image);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Quick pre-check: can {@link NativeImage#read(byte[])} decode these bytes?
     * Avoids registering broken textures (purple-black missing texture) when the
     * CDN returns a format STBImage doesn't support (e.g. WebP).
     *
     * @deprecated Prefer {@link #decodeBytes(byte[])} which decodes once and
     *             keeps the result; this method decodes-and-discards.
     */
    @Deprecated
    public static boolean canDecode(byte[] bytes) {
        if (bytes == null || bytes.length < 8) return false;
        try {
            NativeImage image = NativeImage.read(ensurePng(bytes));
            image.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Decode raw image bytes (PNG, JPEG, etc.) into a {@link NativeImage}.
     * <p>
     * Thread-safe: call from any thread. The returned NativeImage must be
     * registered on the render thread via {@link #registerImage} or
     * {@link #registerCircularImage}, then closed by the caller if not
     * registered.
     */
    public static NativeImage decodeBytes(byte[] bytes) throws IOException {
        return NativeImage.read(ensurePng(bytes));
    }

    /**
     * Register an already-decoded {@link NativeImage} as a dynamic texture.
     * <strong>Must be called on the render thread.</strong>
     * <p>
     * This is a lightweight call that only touches the TextureManager;
     * heavy decoding should happen off-thread via {@link #decodeBytes}.
     */
    public static void registerImage(Identifier id, NativeImage image) {
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(null, image));
    }

    /**
     * Register an already-decoded {@link NativeImage} as a circular dynamic
     * texture (alpha-masks a circle, then registers).
     * <strong>Must be called on the render thread.</strong>
     * <p>
     * The source NativeImage is closed after the circle is extracted.
     */
    public static void registerCircularImage(Identifier id, NativeImage src) {
        NativeImage circle = makeCircular(src);
        src.close();
        registerImage(id, circle);
    }

    /**
     * Decode raw bytes and produce a circular NativeImage in one step.
     * Thread-safe: call from any thread. The returned image must be registered
     * on the render thread via {@link #registerImage}.
     */
    public static NativeImage decodeCircular(byte[] bytes) throws IOException {
        NativeImage src = decodeBytes(bytes);
        NativeImage circle = makeCircular(src);
        src.close();
        return circle;
    }

    public static void registerDynamicImage(Identifier identifier, NativeImage image) {
        Minecraft.getInstance().getTextureManager().register(identifier, new DynamicTexture(null, image));
    }

    /**
     * Register a circular (alpha-masked) variant of the given image bytes.
     * Pixels outside the inscribed circle become fully transparent.
     */
    public static void registerCircularDynamicImage(Identifier identifier, byte[] pngOrJpegBytes) {
        try {
            NativeImage src = NativeImage.read(ensurePng(pngOrJpegBytes));
            NativeImage circle = makeCircular(src);
            src.close();
            registerDynamicImage(identifier, circle);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Minecraft 26.2's NativeImage.read() unconditionally calls
     * {@code PngInfo.validateHeader(ByteBuffer)} before invoking STB, so it
     * only accepts PNG-formatted bytes. Use ImageIO to transcode JPEG / GIF /
     * BMP / WebP (anything ImageIO supports) to PNG first.
     */
    private static byte[] ensurePng(byte[] raw) throws IOException {
        if (isPng(raw)) {
            return raw; // already PNG — fast path
        }
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw));
        if (img == null) {
            throw new IOException("ImageIO cannot decode image");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream(raw.length);
        if (!ImageIO.write(img, "PNG", bos)) {
            throw new IOException("ImageIO PNG encode failed");
        }
        return bos.toByteArray();
    }

    private static boolean isPng(byte[] bytes) {
        return bytes != null && bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47;
    }

    public static NativeImage makeCircular(NativeImage src) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        int size = Math.min(sw, sh);
        int ox = (sw - size) / 2;
        int oy = (sh - size) / 2;
        NativeImage out = new NativeImage(size, size, true);
        float cx = (size - 1) * 0.5f;
        float cy = (size - 1) * 0.5f;
        float r = size * 0.5f;
        float r2 = r * r;
        // Soft edge ~1px for less jagged circle on small avatars.
        float feather = 1.25f;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx;
                float dy = y - cy;
                float d2 = dx * dx + dy * dy;
                if (d2 > (r + feather) * (r + feather)) {
                    out.setPixel(x, y, 0);
                    continue;
                }
                int argb = src.getPixel(x + ox, y + oy);
                if (d2 >= (r - feather) * (r - feather)) {
                    float d = (float) Math.sqrt(d2);
                    float aScale = 1f - Math.max(0f, Math.min(1f, (d - (r - feather)) / (feather * 2f)));
                    int a = (argb >>> 24) & 0xFF;
                    int na = Math.max(0, Math.min(255, Math.round(a * aScale)));
                    argb = (na << 24) | (argb & 0x00FFFFFF);
                }
                out.setPixel(x, y, argb);
            }
        }
        return out;
    }
}
