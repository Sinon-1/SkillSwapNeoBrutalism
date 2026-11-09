package campus.skillswap.ui.media;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.WritableImage;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.io.File;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * OPTIONAL: plays reels inside SkillSwap Campus using JavaFX.
 * Videos finds this class by name; if it (or JavaFX) is missing, the app shows a message (videos never open in another program).
 * JavaFX plays MP4 (H.264 + AAC), M4V and MOV(H.264). If a file can't be decoded, the app shows why.
 */
public class VideoPlayerFX {
    /** Autoplaying, looping video. Click the video to pause / resume. */
    public static JComponent create(File f, int w, int h) { return create(f, w, h, msg -> { }); }
    /** Same, but on a playback error calls onFail(reason) on the Swing thread instead of switching to another app. */
    public static JComponent create(File f, int w, int h, java.util.function.Consumer<String> onFail) {
        JFXPanel panel = new JFXPanel();                 // also starts the JavaFX runtime
        Platform.setImplicitExit(false);
        panel.setPreferredSize(new Dimension(w, h));
        Platform.runLater(() -> {
            try {
                Media media = new Media(f.toURI().toString());
                MediaPlayer mp = new MediaPlayer(media);
                mp.setCycleCount(MediaPlayer.INDEFINITE);
                mp.setAutoPlay(true);
                media.setOnError(() -> { String m = String.valueOf(media.getError()); SwingUtilities.invokeLater(() -> onFail.accept(m)); });
                mp.setOnError(() -> { String m = String.valueOf(mp.getError()); SwingUtilities.invokeLater(() -> onFail.accept(m)); });
                MediaView mv = new MediaView(mp);
                mv.setPreserveRatio(true);
                mv.setFitWidth(w);
                mv.setFitHeight(h);
                mv.setOnMouseClicked(e -> { if (mp.getStatus() == MediaPlayer.Status.PLAYING) mp.pause(); else mp.play(); });
                StackPane root = new StackPane(mv);
                root.setStyle("-fx-background-color: black;");
                panel.setScene(new Scene(root, w, h, Color.BLACK));
                panel.putClientProperty("mp", mp);
            } catch (Throwable t) {
                SwingUtilities.invokeLater(() -> onFail.accept(String.valueOf(t)));
            }
        });
        return panel;
    }


    /** Grabs one frame (about half a second in) as an image. Blocks up to 10 s, so call it from a background thread. Returns null on failure. */
    public static BufferedImage thumbnail(File f, int w, int h) {
        new JFXPanel();                                   // starts the JavaFX runtime
        Platform.setImplicitExit(false);
        final BufferedImage[] out = new BufferedImage[1]; final CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                MediaPlayer mp = new MediaPlayer(new Media(f.toURI().toString())); mp.setMute(true);
                MediaView mv = new MediaView(mp); mv.setPreserveRatio(true); mv.setFitWidth(w); mv.setFitHeight(h);
                StackPane root = new StackPane(mv); new Scene(root, w, h, Color.BLACK);
                final boolean[] taken = new boolean[1];
                mp.currentTimeProperty().addListener((obs, o, n) -> {
                    if (taken[0] || n.toSeconds() < 0.3) return; taken[0] = true; mp.pause();
                    try { WritableImage img = root.snapshot(null, null); out[0] = SwingFXUtils.fromFXImage(img, null); } catch (Throwable t) { /* no frame */ }
                    mp.dispose(); done.countDown();
                });
                mp.setOnError(() -> done.countDown());
                mp.play();
            } catch (Throwable t) { done.countDown(); }
        });
        try { done.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { /* give up */ }
        return out[0];
    }

    public static void stop(JComponent c) {
        Platform.runLater(() -> {
            Object mp = c.getClientProperty("mp");
            if (mp instanceof MediaPlayer) { ((MediaPlayer) mp).stop(); ((MediaPlayer) mp).dispose(); }
        });
    }
}