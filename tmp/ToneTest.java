import javax.sound.sampled.*;
import java.util.Arrays;

public class ToneTest {
    public static void main(String[] args) throws Exception {
        AudioFormat fmt = new AudioFormat(48000, 16, 2, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
        Mixer.Info device = Arrays.stream(AudioSystem.getMixerInfo())
                .filter(m -> m.getName().toLowerCase().contains("realme"))
                .filter(m -> AudioSystem.getMixer(m).isLineSupported(info))
                .findFirst().orElseThrow(() -> new IllegalStateException("Buds not found"));
        System.out.println("Playing on: " + device.getName());
        SourceDataLine line = (SourceDataLine) AudioSystem.getMixer(device).getLine(info);
        line.open(fmt);
        line.start();
        int frames = 48000 * 2;
        byte[] buf = new byte[frames * 4];
        for (int i = 0; i < frames; i++) {
            short s = (short) (Math.sin(2 * Math.PI * 440 * i / 48000.0) * 8000);
            for (int ch = 0; ch < 2; ch++) {
                buf[i * 4 + ch * 2] = (byte) s;
                buf[i * 4 + ch * 2 + 1] = (byte) (s >> 8);
            }
        }
        line.write(buf, 0, buf.length);
        line.drain();
        line.close();
    }
}