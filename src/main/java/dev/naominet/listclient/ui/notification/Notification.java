package dev.naominet.listclient.ui.notification;

record Notification(long id, NotificationType type, String message, long durationMs) {
}
