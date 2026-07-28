package dev.naominet.listclient.utils;

import dev.naominet.listclient.ui.notification.NotificationManager;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClientUtils {
    private static final Queue<Runnable> taskQueue = new ConcurrentLinkedQueue<>();

    public static void sendMessage(String msg){
        NotificationManager.instance.info(stripFormatting(msg));
    }

    private static String stripFormatting(String text) {
        return text == null ? "" : text.replaceAll("\u00A7[0-9A-FK-ORa-fk-or]", "");
    }

    public static void addNewMissonToRenderThread(Runnable runnable){
        taskQueue.add(runnable);
    }

    // 每帧执行队列任务（在客户端主线程中调用）
    public static void runTasks() {
        Runnable task;
        while ((task = taskQueue.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
