package campus.skillswap.ui.media;

import static campus.skillswap.ui.media.Videos.openExternal;

import javafx.application.Platform;
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

/**
 * OPTIONAL: plays reels inside SkillSwap Campus using JavaFX.
 * Videos finds this class by name; if it (or JavaFX) is missing, videos open in the computer's default player instead.
 * JavaFX plays MP4 (H.264 + AAC), M4V and MOV(H.264). If a file can't be decoded, it falls back to the default player automatically.
 */
public class VideoPlayerFX {
    /** Autoplaying, looping video. Click the video to pause / resume. */
    public static JComponent create(File f, int w, int h) {
        JFXPanel panel = new JFXPanel();                 // also starts the JavaFX runtime
        Platform.setImplicitExit(false);
        panel.setPreferredSize(new Dimension(w, h));
        Platform.runLater(() -> {
            try {
                MediaPlayer mp = new MediaPlayer(new Media(f.toURI().toString()));
                mp.setCycleCount(MediaPlayer.INDEFINITE);
                mp.setAutoPlay(true);
                mp.setOnError(() -> SwingUtilities.invokeLater(() -> openExternal(f)));
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
                SwingUtilities.invokeLater(() -> openExternal(f));
            }
        });
        return panel;
    }

    public static void stop(JComponent c) {
        Platform.runLater(() -> {
            Object mp = c.getClientProperty("mp");
            if (mp instanceof MediaPlayer) { ((MediaPlayer) mp).stop(); ((MediaPlayer) mp).dispose(); }
        });
    }
}