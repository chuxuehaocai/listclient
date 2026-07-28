package dev.naominet.listclient.eventBus.events;

import dev.naominet.listclient.eventBus.Event;

public class EventVelocity extends Event {
    private final int entityId;
    private double x, y, z;

    public EventVelocity(int entityId, double x, double y, double z) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getEntityId() { return entityId; }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }

    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setZ(double z) { this.z = z; }
}
