package campus.skillswap.ui.media;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static campus.skillswap.ui.Theme.*;

/**
 * Makes the "choose a file" windows show what the files look like:
 *  - install(fc): every picture and video in the file list gets a small thumbnail icon (loaded in the background while you scroll);
 *  - ImagePreview: a big preview panel on the right that shows the picture you click (or move to with the arrow keys).
 */
public class PickerPreview {
    static final int T = 44;                                                   // thumbnail size in the list
    static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "picker-thumbs"); t.setDaemon(true); return t; });
    static final Map<String, Icon> ICONS = new ConcurrentHashMap<>();
    static final Set<String> BUSY = ConcurrentHashMap.newKeySet();
    static final Icon NONE = new ImageIcon();                                  // "no thumbnail possible": keep the normal file icon

    public static boolean isImage(File f) {
        if (f == null || !f.isFile()) return false; String n = f.getName().toLowerCase();
        for (String x : new String[]{".jpg", ".jpeg", ".png", ".gif", ".bmp", ".wbmp"}) if (n.endsWith(x)) return true; return false;
    }
    static boolean isVideo(File f) { return f != null && Videos.ChooserPreview.isVideo(f); }

    /** Reads a picture already shrunk while decoding (fast even for 20 MB photos). Returns null if it is not a picture. */
    static BufferedImage readScaled(File f, int target) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(f)) {
            if (in == null) return null; Iterator<ImageReader> it = ImageIO.getImageReaders(in); if (!it.hasNext()) return null; ImageReader r = it.next();
            try {
                r.setInput(in, true, true); int w = r.getWidth(0), h = r.getHeight(0); int sub = Math.max(1, Math.min(w, h) / Math.max(1, target));
                ImageReadParam p = r.getDefaultReadParam(); if (sub > 1) p.setSourceSubsampling(sub, sub, 0, 0); return r.read(0, p);
            } finally { r.dispose(); }
        }
    }
    /** Centre-crops to a square and scales to n x n with a thin ink border. */
    static ImageIcon square(BufferedImage im, int n) {
        int s = Math.min(im.getWidth(), im.getHeight()); BufferedImage o = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB); Graphics2D g = o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(im, 0, 0, n, n, (im.getWidth() - s) / 2, (im.getHeight() - s) / 2, (im.getWidth() + s) / 2, (im.getHeight() + s) / 2, null);
        g.setColor(INK); g.setStroke(new BasicStroke(2f)); g.drawRect(1, 1, n - 2, n - 2); g.dispose(); return new ImageIcon(o);
    }

    /** Thumbnails next to every picture / video in the file list. */
    public static void install(JFileChooser fc) {
        fc.setFileView(new FileView() {
            public Icon getIcon(File f) {
                try {
                    if (f == null || !f.isFile()) return null; boolean img = isImage(f), vid = !img && isVideo(f); if (!img && !vid) return null;
                    String key = f.getAbsolutePath() + "|" + f.lastModified(); Icon ic = ICONS.get(key);
                    if (ic != null) return ic == NONE ? null : ic;
                    if (BUSY.add(key)) POOL.submit(() -> {
                        Icon res = NONE;
                        try { BufferedImage im = img ? readScaled(f, T * 2) : (f.length() > 150L * 1024 * 1024 ? null : Videos.grabFrame(f)); if (im != null) res = square(im, T); } catch (Throwable t) { /* keep the normal icon */ }
                        if (ICONS.size() > 800) ICONS.clear(); ICONS.put(key, res); BUSY.remove(key); SwingUtilities.invokeLater(fc::repaint);
                    });
                } catch (Throwable t) { /* keep the normal icon */ }
                return null;                                                     // normal icon until the thumbnail is ready
            }
        });
    }

    /** Right-hand panel of the file window: shows the picture you clicked, large. */
    public static class ImagePreview extends JPanel implements java.beans.PropertyChangeListener {
        final JLabel pic = new JLabel("", SwingConstants.CENTER), info = lbl(" ", Font.BOLD, 11, TXT); int tok;
        public ImagePreview(JFileChooser fc) {
            super(new BorderLayout(0, 8)); setBackground(PANEL); setBorder(new EmptyBorder(8, 10, 8, 10)); setPreferredSize(new Dimension(318, 0));
            JPanel stage = new JPanel(new BorderLayout()); stage.setBackground(new Color(0x1B1E2B)); stage.setBorder(new LineBorder(INK, BW)); Dimension d = new Dimension(296, 296); stage.setPreferredSize(d); stage.setMinimumSize(d);
            pic.setForeground(Color.WHITE); pic.setFont(f(Font.PLAIN, 12)); stage.add(pic);
            JPanel top = vbox(); put(top, lbl("Preview", Font.BOLD, 14, TXT)); gap(top, 6); put(top, stage); gap(top, 8); put(top, info);
            JPanel col = new JPanel(new BorderLayout()); col.setOpaque(false); col.add(top, BorderLayout.NORTH); add(col);
            empty(); fc.addPropertyChangeListener(this);
        }
        void empty() { pic.setIcon(null); pic.setText("<html><body style='width:230px;text-align:center'>Click a picture to see it here</body></html>"); info.setText(" "); }
        public void propertyChange(java.beans.PropertyChangeEvent e) {
            String p = e.getPropertyName();
            if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(p)) load((File) e.getNewValue()); else if (JFileChooser.DIRECTORY_CHANGED_PROPERTY.equals(p)) load(null);
        }
        void load(File f) {
            final int my = ++tok; if (!isImage(f)) { empty(); return; }
            info.setText(f.getName() + "  (" + Videos.mb(f.length()) + ")"); pic.setIcon(null); pic.setText("Loading...");
            POOL.submit(() -> {
                BufferedImage im = null; try { im = readScaled(f, 600); } catch (Throwable t) { /* shown below */ }
                final BufferedImage r = im;
                SwingUtilities.invokeLater(() -> {
                    if (my != tok) return; if (r == null) { pic.setText("Can't show this picture"); return; }
                    double sc = Math.min(288.0 / r.getWidth(), 288.0 / r.getHeight()); int w = Math.max(1, (int) (r.getWidth() * sc)), h = Math.max(1, (int) (r.getHeight() * sc));
                    pic.setText(""); pic.setIcon(new ImageIcon(r.getScaledInstance(w, h, Image.SCALE_SMOOTH)));
                });
            });
        }
    }
}
