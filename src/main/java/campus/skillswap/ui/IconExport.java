package campus.skillswap.ui;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the SkillSwap mark to disk in the formats the packagers need.
 *
 * <p>The artwork is {@code src/main/resources/campus/skillswap/app-icon.png}; {@link
 * Theme#appIcon(int)} scales it for the window, taskbar and installer icons so they can never
 * drift apart. Run it before jpackage:
 *
 * <pre>java -cp target/classes campus.skillswap.ui.IconExport target/icon</pre>
 *
 * <p>Produces {@code icon-1024.png}, {@code icon.png} (512 px), {@code icon.ico} (Windows, 16-256 px,
 * PNG-compressed entries) and {@code icon.iconset/} (macOS {@code iconutil} input).
 */
public final class IconExport {

    private static final int[] ICO_SIZES = {16, 24, 32, 48, 64, 128, 256};

    private IconExport() { }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "target/icon").toAbsolutePath();
        Files.createDirectories(dir);
        writePng(Theme.appIcon(1024), dir.resolve("icon-1024.png"));
        writePng(Theme.appIcon(512), dir.resolve("icon.png"));
        writePng(Theme.appIcon(256), dir.resolve("icon-256.png"));
        writeIco(dir.resolve("icon.ico"), ICO_SIZES);
        writeIconset(dir.resolve("icon.iconset"));
        System.out.println("SkillSwap icon written to " + dir);
    }

    static void writePng(BufferedImage im, Path out) throws IOException {
        ImageIO.write(im, "png", out.toFile());
    }

    /** Windows .ico with PNG-compressed entries (supported by Windows Vista and newer). */
    static void writeIco(Path out, int[] sizes) throws IOException {
        List<byte[]> images = new ArrayList<>(sizes.length);
        for (int s : sizes) images.add(png(Theme.appIcon(s)));

        try (OutputStream o = Files.newOutputStream(out)) {
            le16(o, 0);                 // reserved
            le16(o, 1);                 // type: icon
            le16(o, sizes.length);      // image count
            int offset = 6 + 16 * sizes.length;
            for (int i = 0; i < sizes.length; i++) {
                int s = sizes[i];
                o.write(s >= 256 ? 0 : s);  // 0 means 256 in the ICO directory
                o.write(s >= 256 ? 0 : s);
                o.write(0);                 // palette size (0 = true colour)
                o.write(0);                 // reserved
                le16(o, 1);                 // colour planes
                le16(o, 32);                // bits per pixel
                le32(o, images.get(i).length);
                le32(o, offset);
                offset += images.get(i).length;
            }
            for (byte[] img : images) o.write(img);
        }
    }

    /** Iconset layout that macOS {@code iconutil -c icns} expects. */
    static void writeIconset(Path dir) throws IOException {
        Files.createDirectories(dir);
        int[] base = {16, 32, 128, 256, 512};
        for (int s : base) {
            writePng(Theme.appIcon(s), dir.resolve("icon_" + s + "x" + s + ".png"));
            writePng(Theme.appIcon(s * 2), dir.resolve("icon_" + s + "x" + s + "@2x.png"));
        }
    }

    static byte[] png(BufferedImage im) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(4096);
        ImageIO.write(im, "png", buf);
        return buf.toByteArray();
    }

    static void le16(OutputStream o, int v) throws IOException {
        o.write(v & 0xFF); o.write((v >>> 8) & 0xFF);
    }

    static void le32(OutputStream o, int v) throws IOException {
        o.write(v & 0xFF); o.write((v >>> 8) & 0xFF); o.write((v >>> 16) & 0xFF); o.write((v >>> 24) & 0xFF);
    }
}
