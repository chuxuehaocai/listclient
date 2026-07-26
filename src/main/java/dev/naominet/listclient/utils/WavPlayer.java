package dev.naominet.listclient.utils;

import dev.naominet.listclient.core.ListClient;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class WavPlayer {
    private static ArrayList<Clip> clips = new ArrayList<>();

    public static void stopCurrentPlaying(){
        clips.forEach(Clip::close);
    }
    public static void playWav(String name, boolean infinityLoop) {
        try (InputStream audioSrc = ListClient.class.getResourceAsStream("/assets/listclient/sound/" + name)) {
            if (audioSrc == null) {
                System.err.println("not found "+name);
                return;
            }
            // 用 BufferedInputStream 包装，避免 mark/reset 问题
            InputStream bufferedIn = new java.io.BufferedInputStream(audioSrc);

            AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            if(infinityLoop)
                clip.loop(114514);
            clip.start();
            clips.add(clip);

            // clip.close();
            audioStream.close();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            e.printStackTrace();
        }
    }
}
