package campus.skillswap.ui.media;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;

public class ScreenCapture {
    static final int FPS = 8, MAX_WIDTH = 1280;
    volatile boolean run;

    public void start(Consumer<byte[]> onJpeg, Consumer<String> onFail) {
        run = true;
        Thread t = new Thread(() -> loop(onJpeg, onFail), "call-screen"); t.setDaemon(true); t.start();
    }

    public void stop() { run = false; }

    void loop(Consumer<byte[]> onJpeg, Consumer<String> onFail) {
        try {
            Robot robot = new Robot();
            Rectangle bounds = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            long delay = 1000L / FPS;
            while (run) {
                long started = System.currentTimeMillis();
                BufferedImage shot = robot.createScreenCapture(bounds);
                byte[] jpeg = encode(shot);
                if (jpeg == null) throw new IOException("Could not encode the screen");
                if (run) onJpeg.accept(jpeg);
                long left = delay - (System.currentTimeMillis() - started);
                if (left > 0) Thread.sleep(left);
            }
        } catch (Throwable e) {
            if (run && onFail != null) onFail.accept("Screen sharing could not start. Allow screen recording / accessibility for SkillSwap and try again.");
        }
    }

    static byte[] encode(BufferedImage src) {
        try {
            int w = src.getWidth(), h = src.getHeight();
            if (w > MAX_WIDTH) { h = Math.max(1, Math.round(h * (MAX_WIDTH / (float) w))); w = MAX_WIDTH; }
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null); g.dispose();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(0.72f);
            ByteArrayOutputStream buf = new ByteArrayOutputStream(128 * 1024);
            try (ImageOutputStream outStream = ImageIO.createImageOutputStream(buf)) {
                writer.setOutput(outStream); writer.write(null, new IIOImage(out, null, null), p);
            } finally { writer.dispose(); }
            return buf.toByteArray();
        } catch (Throwable e) {
            return null;
        }
    }
}
