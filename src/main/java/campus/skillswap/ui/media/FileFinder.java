package campus.skillswap.ui.media;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import static campus.skillswap.config.AppConfig.*;
import static campus.skillswap.ui.Theme.*;

/**
 * Side panel for the "choose a file" window:
 *  - search box that finds files by name inside the folder you are looking at (click a hit to select it, double-click to choose it);
 *  - compact preview of pictures and videos, a play button for sounds, and name/kind/size/modified details for every file;
 *  - when several files are selected it shows how many and their total size.
 */
public class FileFinder extends JPanel implements java.beans.PropertyChangeListener {
    static final int MAX_HITS = 40;
    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a");
    static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "file-finder"); t.setDaemon(true); return t; });

    /** Adds the smart search/preview panel to any file chooser (open or save windows). */
    public static FileFinder install(JFileChooser fc) { FileFinder p = new FileFinder(fc); fc.setAccessory(p); return p; }
    /** Folder used by the last file window on this computer. */
    public static File lastDir() {
        try { File d = new File(c("file.dir", System.getProperty("user.home"))); if (d.isDirectory()) return d; } catch (Exception e) { }
        return new File(System.getProperty("user.home"));
    }
    public static void remember(JFileChooser fc) { rememberDir(fc.getCurrentDirectory()); }
    public static void rememberDir(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        try { cfg.setProperty("file.dir", dir.getAbsolutePath()); saveCfg(); } catch (Exception e) { }
    }

    final JFileChooser fc;
    final JTextField q = field();
    final DefaultListModel<File> model = new DefaultListModel<>();
    final JList<File> hits = new JList<>(model);
    final JLabel title = lbl("FIND A FILE", Font.BOLD, 11, MUTED);
    final JPanel stage = new JPanel();
    final JLabel shot = new JLabel("", SwingConstants.CENTER);
    final JButton play = ghost("\u25B6  Play preview", this::playNow);
    JComponent player; File current;
    final JLabel info = lbl(" ", Font.BOLD, 11, TXT), meta = lbl(" ", Font.PLAIN, 11, MUTED);
    final JComboBox<String> ftype = combo(new String[]{"Any type", "Pictures", "Videos", "Audio", "Docs / other"});
    final JComboBox<String> fsort = combo(new String[]{"A-Z", "Newest", "Largest"});
    int tok;

    public FileFinder(JFileChooser fc) {
        super(new BorderLayout(0, 8));
        this.fc = fc;
        setBackground(PANEL); setBorder(new EmptyBorder(8, 10, 8, 10)); setPreferredSize(new Dimension(334, 0));

        q.setToolTipText("Type part of a file name to find it in this folder");
        q.addActionListener(e -> { if (model.size() >= 1) { fc.setSelectedFile(model.get(0)); if (model.size() == 1) fc.approveSelection(); } });
        q.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); } public void removeUpdate(DocumentEvent e) { refresh(); } public void changedUpdate(DocumentEvent e) { refresh(); }
        });

        final JComboBox<String>[] combos = new JComboBox[]{ftype, fsort};
        for (JComboBox<String> cb : combos) { cb.setFont(f(Font.PLAIN, 11)); cb.addActionListener(e -> refresh()); }
        JPanel filters = row(); filters.add(ftype); filters.add(fsort);

        hits.setFont(f(Font.PLAIN, 12)); hits.setBackground(FIELD); hits.setForeground(TXT); hits.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hits.setCellRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                File file = (File) value; setText(file.getName()); setToolTipText(file.getAbsolutePath());
                setFont(f(Font.PLAIN, 12)); setBackground(selected ? YEL : FIELD); setForeground(selected ? ONACC : TXT); setBorder(new EmptyBorder(3, 6, 3, 6));
                return this;
            }
        });
        hits.addListSelectionListener(e -> { if (!e.getValueIsAdjusting() && hits.getSelectedValue() != null) fc.setSelectedFile(hits.getSelectedValue()); });
        hits.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { if (e.getClickCount() >= 2 && hits.getSelectedValue() != null) fc.approveSelection(); }
        });
        JScrollPane hs = new JScrollPane(hits, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        hs.setPreferredSize(new Dimension(300, 112)); hs.setBorder(new LineBorder(INK, BW)); hs.getViewport().setBackground(FIELD);

        stage.setLayout(new GridBagLayout());
        stage.setBackground(new Color(0x1B1E2B)); stage.setBorder(new LineBorder(INK, BW)); stage.setPreferredSize(new Dimension(300, 136));
        shot.setForeground(Color.WHITE); shot.setFont(f(Font.PLAIN, 12)); stage.add(shot);
        play.setFont(f(Font.BOLD, 11)); play.setBorder(new EmptyBorder(3, 10, 3, 10)); play.setVisible(false); play.setEnabled(false);

        JPanel top = vbox(); put(top, title); gap(top, 4); put(top, q); gap(top, 4); put(top, filters); gap(top, 4); put(top, hs);
        JPanel bot = vbox(); put(bot, stage); gap(bot, 4); put(bot, play); gap(bot, 4); put(bot, info); gap(bot, 2); put(bot, meta);
        add(top, BorderLayout.NORTH); add(bot, BorderLayout.CENTER);
        fc.addPropertyChangeListener(this);
        refresh(); showFile(fc.getSelectedFile());
    }

    /** Live-filter the files in the folder the chooser is showing. */
    void refresh() {
        String query = q.getText().trim().toLowerCase(Locale.ROOT);
        model.clear();
        File dir = fc.getCurrentDirectory();
        if (query.isEmpty()) { title.setText("FIND A FILE"); title.setForeground(MUTED); return; }
        File[] all = dir == null ? null : dir.listFiles();
        List<File> found = new ArrayList<>();
        int type = ftype.getSelectedIndex();
        if (all != null) for (File file : all) {
            if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).contains(query)) continue;
            String k = ChatAttachments.kind(file);
            boolean img = "image".equals(k), vid = "video".equals(k), aud = Sound.isAudio(file);
            if (type == 1 && !img) continue;
            if (type == 2 && !vid) continue;
            if (type == 3 && !aud) continue;
            if (type == 4 && (img || vid || aud)) continue;
            found.add(file);
        }
        switch (fsort.getSelectedIndex()) {
            case 1: found.sort(Comparator.comparingLong(File::lastModified).reversed()); break;
            case 2: found.sort(Comparator.comparingLong(File::length).reversed()); break;
            default: found.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        }
        for (int i = 0; i < Math.min(found.size(), MAX_HITS); i++) model.addElement(found.get(i));
        boolean none = found.isEmpty();
        title.setText(none ? "NO MATCHES IN THIS FOLDER" : found.size() + " MATCH" + (found.size() == 1 ? "" : "ES") + (found.size() > MAX_HITS ? " (first " + MAX_HITS + ")" : ""));
        title.setForeground(none ? PINKT : GREEN);
    }

    public void propertyChange(java.beans.PropertyChangeEvent e) {
        String p = e.getPropertyName();
        if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(p) || JFileChooser.SELECTED_FILES_CHANGED_PROPERTY.equals(p)) showFile(fc.getSelectedFile());
        else if (JFileChooser.DIRECTORY_CHANGED_PROPERTY.equals(p)) { refresh(); showFile(null); }
    }

    void showFile(File selected) {
        stopPlayer();
        File[] many = fc.getSelectedFiles();
        File file = selected; current = file;
        if (many != null && many.length > 1) {
            long total = 0; for (File x : many) total += x.length();
            info.setText(many.length + " files selected"); meta.setText(Videos.mb(total) + " in total - press Open to attach them all");
            file = many[0]; current = file;
        } else {
            String kind = file == null ? "" : Sound.isAudio(file) ? "AUDIO" : ChatAttachments.kind(file).toUpperCase();
            info.setText(file == null ? " " : file.getName());
            meta.setText(file == null ? " " : kind + "  \u2022  " + Videos.mb(file.length()) + "  \u2022  " + STAMP.format(Instant.ofEpochMilli(file.lastModified()).atZone(ZoneId.systemDefault())));
        }
        boolean video = file != null && file.isFile() && "video".equals(ChatAttachments.kind(file));
        boolean audio = file != null && file.isFile() && Sound.isAudio(file);
        play.setVisible(video || audio); play.setEnabled(video || audio);
        play.setText(audio ? "\u25B6  Play sound" : "\u25B6  Play preview");
        if (video) play.setToolTipText(Videos.playProblem(file) == null ? "Play this video in the app" : "Try playing this video in the app");
        else if (audio) play.setToolTipText("Listen to this sound here");
        final int my = ++tok;
        if (file == null || !file.isFile()) { shot.setIcon(null); shot.setText("<html><body style='width:250px;text-align:center'>Click a file to preview it</body></html>"); return; }
        final File target = file;
        if (audio) { setAudioTile(target, "Sound file - press Play sound to listen"); return; }
        shot.setIcon(null); shot.setText("Loading preview...");
        POOL.submit(() -> {
            BufferedImage im = null;
            try {
                if (PickerPreview.isImage(target)) im = PickerPreview.readScaled(target, 460);
                else if ("video".equals(ChatAttachments.kind(target)) && target.length() <= 150L * 1024 * 1024) im = Videos.grabFrame(target);
            } catch (Throwable t) { /* no preview for this file */ }
            final BufferedImage r = im;
            SwingUtilities.invokeLater(() -> {
                if (my != tok) return;
                if (r == null) {
                    shot.setIcon(null);
                    shot.setText("<html><body style='width:250px;text-align:center'>" + ChatAttachments.kind(target).toUpperCase() + " file<br><b>" + escape(target.getName()) + "</b><br><span style='font-size:10px'>No picture preview available</span></body></html>");
                    return;
                }
                double sc = Math.min(292.0 / r.getWidth(), 128.0 / r.getHeight());
                shot.setText(""); shot.setIcon(new ImageIcon(r.getScaledInstance(Math.max(1, (int) (r.getWidth() * sc)), Math.max(1, (int) (r.getHeight() * sc)), Image.SCALE_SMOOTH)));
            });
        });
    }

    void setAudioTile(File file, String hint) {
        shot.setIcon(null);
        shot.setText("<html><body style='width:250px;text-align:center'><b>" + escape(file.getName()) + "</b><br><span style='font-size:10px'>" + escape(hint) + "</span></body></html>");
    }

    /** Play the selected video or sound inside the small preview stage. */
    void playNow() {
        File file = current; if (file == null || !file.isFile()) return;
        stopPlayer();
        final int my = ++tok;
        if (Sound.isAudio(file)) {
            play.setEnabled(false);
            setAudioTile(file, "Playing...");
            final File target = file;
            Sound.playPreview(target, why -> {
                if (my != tok) return;
                setAudioTile(target, "Can't play this sound here: " + (why == null ? "unknown problem" : why));
                play.setEnabled(true);
            }, () -> {
                if (my != tok) return;
                setAudioTile(target, "Sound file - press Play sound to listen");
                play.setEnabled(true);
            });
            return;
        }
        JComponent fx = Videos.fxPlayer(file, 292, 136, why -> SwingUtilities.invokeLater(() -> {
            if (my != tok) return;
            stopPlayer(); shot.setIcon(null);
            shot.setText("<html><body style='width:250px;text-align:center'>Can't play this video here.<br><span style='font-size:10px'>" + escape(why == null ? "" : why.split("\n")[0]) + "</span></body></html>");
        }));
        if (fx == null) { shot.setIcon(null); shot.setText("<html><body style='width:250px;text-align:center'>The in-app player is not available on this computer.</body></html>"); return; }
        player = fx; shot.setIcon(null);
        stage.removeAll(); stage.setLayout(new BorderLayout()); stage.add(fx); stage.revalidate(); stage.repaint();
        play.setEnabled(false);
    }

    public void stopPlayer() {
        Sound.stopPreview();
        if (player == null) return;
        Videos.fxStop(player); player = null;
        stage.removeAll(); stage.setLayout(new GridBagLayout()); stage.add(shot); stage.revalidate(); stage.repaint();
    }

    static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
