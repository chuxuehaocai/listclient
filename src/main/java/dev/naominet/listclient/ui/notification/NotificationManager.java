package dev.naominet.listclient.ui.notification;

import dev.naominet.listclient.eventBus.EventManager;
import dev.naominet.listclient.eventBus.EventTarget;
import dev.naominet.listclient.eventBus.events.EventRender2D;
import dev.naominet.listclient.ui.theme.Icons;
import dev.naominet.listclient.ui.theme.M3;
import dev.naominet.listclient.ui.theme.MonetTheme;
import dev.naominet.listclient.utils.AnimationUtils;
import dev.naominet.listclient.utils.font.TTFFontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class NotificationManager {
    public static final NotificationManager instance = new NotificationManager();

    private static final long ENTER_MS = 220L;
    private static final long EXIT_MS = 180L;
    private static final int MAX_VISIBLE = 8;
    private static final int HEIGHT = 28;
    private static final int GAP = 5;
    private static final int TOP_MARGIN = 12;

    private final AtomicLong nextId = new AtomicLong();
    private final Queue<Notification> incoming = new ConcurrentLinkedQueue<>();
    private final List<ActiveNotification> active = new ArrayList<>();
    private boolean started;

    public synchronized void start() {
        if (started) return;
        started = true;
        EventManager.instance.register(this);
    }

    public synchronized void stop() {
        if (!started) return;
        EventManager.instance.unregister(this);
        started = false;
        clear();
    }

    public long info(String message) {
        return show(NotificationType.INFO, message, 4_000L);
    }

    public long success(String message) {
        return show(NotificationType.SUCCESS, message, 3_500L);
    }

    public long warning(String message) {
        return show(NotificationType.WARNING, message, 5_500L);
    }

    public long error(String message) {
        return show(NotificationType.ERROR, message, 7_000L);
    }

    public long show(NotificationType type, String message, long durationMs) {
        if (message == null || message.isBlank()) return -1L;
        long id = nextId.incrementAndGet();
        incoming.offer(new Notification(id, type, message, Math.max(1_000L, durationMs)));
        return id;
    }

    public void clear() {
        incoming.clear();
        active.clear();
    }

    @EventTarget
    public void onRender2D(EventRender2D event) {
        long now = Util.getMillis();
        drainIncoming(now);
        removeExpired(now);
        if (active.isEmpty()) return;

        MonetTheme.update();
        GuiGraphicsExtractor g = event.getExtractor();
        for (int i = 0; i < active.size(); i++) {
            ActiveNotification entry = active.get(i);
            float visibility = visibility(entry, now);
            float targetY = TOP_MARGIN + i * (HEIGHT + GAP);
            entry.y = AnimationUtils.easeExp(entry.y, targetY, 16f);
            int y = Math.round(entry.y - (1f - visibility) * 12f);
            render(g, entry.notification, visibility, y);
        }
    }

    private void drainIncoming(long now) {
        Notification notification;
        while ((notification = incoming.poll()) != null) {
            if (active.size() >= MAX_VISIBLE) {
                active.removeFirst();
            }
            float initialY = TOP_MARGIN + active.size() * (HEIGHT + GAP) - 12f;
            active.add(new ActiveNotification(notification, now, initialY));
        }
    }

    private void removeExpired(long now) {
        Iterator<ActiveNotification> iterator = active.iterator();
        while (iterator.hasNext()) {
            ActiveNotification entry = iterator.next();
            long total = ENTER_MS + entry.notification.durationMs() + EXIT_MS;
            if (now - entry.startedAt >= total) {
                iterator.remove();
            }
        }
    }

    private static float visibility(ActiveNotification entry, long now) {
        long elapsed = now - entry.startedAt;
        if (elapsed < ENTER_MS) {
            return emphasizedDecelerate(elapsed / (float) ENTER_MS);
        }
        long exitAt = ENTER_MS + entry.notification.durationMs();
        if (elapsed > exitAt) {
            return 1f - emphasizedAccelerate((elapsed - exitAt) / (float) EXIT_MS);
        }
        return 1f;
    }

    private static float emphasizedDecelerate(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private static float emphasizedAccelerate(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * t;
    }

    private void render(GuiGraphicsExtractor g, Notification notification, float visibility, int y) {
        // TTFFontRenderer treats a zero-alpha color as an opaque legacy RGB
        // color. Stop submitting the whole snackbar before fade rounds to zero
        // so its text and surface disappear on exactly the same frame.
        if (visibility <= 1f / 255f) return;

        Minecraft mc = Minecraft.getInstance();
        TTFFontRenderer font = M3.body();
        String text = notification.message();
        int maxTextWidth = Math.max(24, mc.getWindow().getGuiScaledWidth() - 72);
        text = ellipsize(font, text, maxTextWidth);

        int width = Math.max(116, Math.min(mc.getWindow().getGuiScaledWidth() - 24,
                (int) Math.ceil(font.width(text)) + 46));
        int x = (mc.getWindow().getGuiScaledWidth() - width) / 2;
        int radius = M3.SHAPE_M;
        int container = M3.fade(container(notification.type()), visibility);
        int content = M3.fade(content(notification.type()), visibility);

        M3.shadowSoft(g, x, y, width, HEIGHT, radius, visibility);
        M3.roundRect(g, x, y, width, HEIGHT, radius, container);
        Icons.drawCentered(g, icon(notification.type()), 12, x + 17, y + HEIGHT / 2f, content);
        font.drawString(g, text, x + 31, y + (HEIGHT - font.lineHeight()) / 2f, content);
    }

    private static String ellipsize(TTFFontRenderer font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String suffix = "…";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + suffix) > maxWidth) {
            end--;
            if (end > 0 && Character.isLowSurrogate(text.charAt(end))) end--;
        }
        return text.substring(0, Math.max(0, end)) + suffix;
    }

    private static int container(NotificationType type) {
        return switch (type) {
            case INFO -> M3.PRIMARY_CONTAINER;
            case SUCCESS -> M3.TERTIARY;
            case WARNING -> M3.SECONDARY_CONTAINER;
            case ERROR -> M3.ERROR_CONTAINER;
        };
    }

    private static int content(NotificationType type) {
        return switch (type) {
            case INFO -> M3.ON_PRIMARY_CONTAINER;
            case SUCCESS -> M3.ON_TERTIARY;
            case WARNING -> M3.ON_SECONDARY_CONTAINER;
            case ERROR -> M3.ON_ERROR_CONTAINER;
        };
    }

    private static String icon(NotificationType type) {
        return switch (type) {
            case INFO, WARNING -> Icons.INFO;
            case SUCCESS -> Icons.CHECK;
            case ERROR -> Icons.CLOSE;
        };
    }

    private static final class ActiveNotification {
        private final Notification notification;
        private final long startedAt;
        private float y;

        private ActiveNotification(Notification notification, long startedAt, float y) {
            this.notification = notification;
            this.startedAt = startedAt;
            this.y = y;
        }
    }

    private NotificationManager() {
    }
}
