package dev.naominet.listclient.eventBus.events;

import dev.naominet.listclient.eventBus.Event;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class EventRender2D extends Event {
    private final GuiGraphicsExtractor extractor;
    private final DeltaTracker tracker;

    public EventRender2D(GuiGraphicsExtractor extractor, DeltaTracker tracker) {
        this.extractor = extractor;
        this.tracker = tracker;
    }

    public GuiGraphicsExtractor getExtractor() {
        return extractor;
    }

    public DeltaTracker getTracker() {
        return tracker;
    }
}
