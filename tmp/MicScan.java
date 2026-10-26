import javax.sound.sampled.*;

public class MicScan {
    public static void main(String[] args) {
        AudioFormat fmt = new AudioFormat(48000, 16, 2, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
        boolean any = false;
        for (Mixer.Info m : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(m);
            if (!mixer.isLineSupported(info)) continue;
            any = true;
            System.out.printf("%-40s ", m.getName());
            try (TargetDataLine line = (TargetDataLine) mixer.getLine(info)) {
                line.open(fmt);
                line.start();
                byte[] buf = new byte[48000 * 4 * 2]; // 2 seconds
                int n = 0;
                while (n < buf.length) {
                    int r = line.read(buf, n, buf.length - n);
                    if (r <= 0) break;
                    n += r;
                }
                int peak = 0;
                for (int i = 0; i + 1 < n; i += 2) {
                    peak = Math.max(peak, Math.abs((short) ((buf[i] & 0xff) | (buf[i + 1] << 8))));
                }
                System.out.println("peak " + peak + (peak == 0 ? "  <-- digital silence" : ""));
            } catch (Exception e) {
                System.out.println("FAILED: " + e.getMessage());
            }
        }
        if (!any) System.out.println("No capture devices support 48 kHz / 16-bit / stereo");
    }
}