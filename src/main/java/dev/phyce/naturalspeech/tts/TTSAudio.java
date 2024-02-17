package dev.phyce.naturalspeech.tts;

public class TTSAudio {
    private byte[] clip;
    private int distance;

    public TTSAudio(byte[] clip, int distance) {
        this.clip = clip;
        this.distance = Math.max(distance, 0);
    }

    public byte[] getClip() {return clip;}
    public int getDistance() {return distance;}
}
