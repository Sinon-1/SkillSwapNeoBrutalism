package campus.skillswap.ui.media;

import javafx.application.Platform;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
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
    /** Remembered between videos, so muting one reel keeps the next one muted (like Instagram / Facebook). */
    static double volume = 1.0; static boolean muted = false;

    /** Autoplaying, looping video with Meta-style controls (see Controls below). */
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
                mp.setVolume(volume); mp.setMute(muted);
                media.setOnError(() -> { String m = String.valueOf(media.getError()); SwingUtilities.invokeLater(() -> onFail.accept(m)); });
                mp.setOnError(() -> { String m = String.valueOf(mp.getError()); SwingUtilities.invokeLater(() -> onFail.accept(m)); });
                MediaView mv = new MediaView(mp);
                mv.setPreserveRatio(true);
                mv.setFitWidth(w);
                mv.setFitHeight(h);
                Controls c = new Controls(mp, mv, w, h);
                panel.setScene(new Scene(c.root, w, h, Color.BLACK));
                panel.putClientProperty("mp", mp);
                panel.putClientProperty("ctl", c);
            } catch (Throwable t) {
                SwingUtilities.invokeLater(() -> onFail.accept(String.valueOf(t)));
            }
        });
        return panel;
    }

    /** Remote control from Swing (keyboard shortcuts): "toggle", "back", "fwd", "mute". */
    public static void command(JComponent c, String cmd) {
        Platform.runLater(() -> { Object o = c.getClientProperty("ctl"); if (o instanceof Controls) ((Controls) o).run(cmd); });
    }

    // =====================================================================================
    //  Meta-style controls, drawn with JavaFX shapes (no emoji fonts needed).
    //   - click: pause / play              - double-click left / right side: back / forward 10 s
    //   - press and hold: pause while held - press and hold the right third: 2x speed while held
    //   - seek bar: click or drag          - buttons: play/pause, -10, +10, time, speed, mute + volume
    //   - controls fade out while playing; a thin progress line stays at the bottom
    // =====================================================================================
    static final Color PINK = Color.web("#FF4D8D");
    static final double[] SPEEDS = {0.5, 1.0, 1.5, 2.0};

    /** A thin clickable / draggable bar (used for the seek bar and the volume bar). */
    static class Bar extends StackPane {
        final Rectangle track = new Rectangle(), fill = new Rectangle(); final Circle knob = new Circle(6, Color.WHITE);
        final DoubleConsumer onChange; final Consumer<Boolean> onDrag; double ratio; boolean dragging;
        Bar(double width, double hitH, DoubleConsumer onChange, Consumer<Boolean> onDrag) {
            this.onChange = onChange; this.onDrag = onDrag;
            setPrefSize(width, hitH); setMinSize(width, hitH); setMaxSize(width, hitH); setAlignment(Pos.CENTER_LEFT);
            track.setWidth(width); track.setHeight(4); track.setArcWidth(4); track.setArcHeight(4); track.setFill(Color.rgb(255, 255, 255, 0.35));
            fill.setHeight(4); fill.setArcWidth(4); fill.setArcHeight(4); fill.setFill(PINK);
            knob.setVisible(false); knob.setTranslateX(-6);
            getChildren().addAll(track, fill, knob); setCursor(Cursor.HAND);
            setOnMouseEntered(e -> grow(true)); setOnMouseExited(e -> { if (!dragging) grow(false); });
            setOnMousePressed(e -> { dragging = true; grow(true); onDrag.accept(true); move(e.getX()); e.consume(); });
            setOnMouseDragged(e -> { move(e.getX()); e.consume(); });
            setOnMouseReleased(e -> { move(e.getX()); dragging = false; onDrag.accept(false); if (!isHover()) grow(false); e.consume(); });
            setOnMouseClicked(e -> e.consume());
        }
        void grow(boolean big) { double h = big ? 6 : 4; track.setHeight(h); fill.setHeight(h); knob.setVisible(big); }
        void move(double x) { double r = Math.max(0, Math.min(1, x / track.getWidth())); setRatio(r); onChange.accept(r); }
        void setRatio(double r) { ratio = Math.max(0, Math.min(1, r)); fill.setWidth(track.getWidth() * ratio); knob.setTranslateX(track.getWidth() * ratio - 6); }
    }

    static class Controls {
        final MediaPlayer mp; final double w, h; final StackPane root, fb; final Region hit = new Region();
        final VBox bar = new VBox(2); final Rectangle mini = new Rectangle(0, 3);
        final Bar seek; final Text time = new Text("0:00 / 0:00"), badgeTxt = new Text(); final StackPane badge = pill(badgeTxt, 15);
        final StackPane ppBtn, muteBtn; final Text speedTxt = new Text("1x");
        final PauseTransition clickDelay = new PauseTransition(Duration.millis(230)), holdTimer = new PauseTransition(Duration.millis(350)), hideTimer = new PauseTransition(Duration.millis(2600));
        final FadeTransition fade;
        boolean dragging, holdActive, pausedByHold, suppressClick, barShown = true; double savedRate = 1; int zone;
        final double u;   // button size

        Controls(MediaPlayer mp, MediaView mv, double w, double h) {
            this.mp = mp; this.w = w; this.h = h; u = h < 420 ? 28 : 32;
            hit.setStyle("-fx-background-color: transparent;"); hit.setCursor(Cursor.HAND);
            fb = new StackPane(); fb.setMouseTransparent(true);
            badge.setVisible(false); badge.setMouseTransparent(true); StackPane.setAlignment(badge, Pos.TOP_CENTER); StackPane.setMargin(badge, new Insets(14, 0, 0, 0));
            mini.setFill(PINK); mini.setMouseTransparent(true); StackPane.setAlignment(mini, Pos.BOTTOM_LEFT); mini.setVisible(false);

            seek = new Bar(w - 28, 16, r -> { double t = total(); if (t > 0) { mp.seek(Duration.seconds(r * t)); setTime(r * t, t); } }, d -> { dragging = d; if (!d) restartHide(); });
            ppBtn = btn(playShape(u * 0.42), u, null); ppBtn.setOnMouseClicked(e -> { toggle(false); e.consume(); });
            time.setFill(Color.WHITE); time.setFont(Font.font("System", FontWeight.BOLD, 12));
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            speedTxt.setFill(Color.WHITE); speedTxt.setFont(Font.font("System", FontWeight.BOLD, 12));
            StackPane speed = new StackPane(speedTxt); speed.setStyle("-fx-background-color: rgba(255,255,255,0.20); -fx-background-radius: 14;"); speed.setPadding(new Insets(4, 11, 4, 11));
            speed.setCursor(Cursor.HAND); speed.setOnMouseClicked(e -> { cycleSpeed(); e.consume(); }); speed.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            muteBtn = btn(speakerShape(muted || volume == 0), u, this::toggleMute);
            HBox row = new HBox(8, ppBtn, time, sp, speed, muteBtn); row.setAlignment(Pos.CENTER_LEFT);
            bar.getChildren().addAll(row, seek); bar.setPadding(new Insets(36, 14, 6, 14)); bar.setAlignment(Pos.BOTTOM_LEFT);
            bar.setStyle("-fx-background-color: linear-gradient(to top, rgba(0,0,0,0.70), rgba(0,0,0,0));");
            bar.setMaxHeight(Region.USE_PREF_SIZE); StackPane.setAlignment(bar, Pos.BOTTOM_CENTER);
            fade = new FadeTransition(Duration.millis(180), bar);

            root = new StackPane(mv, hit, fb, badge, mini, bar); root.setStyle("-fx-background-color: black;");
            hit.setOnMousePressed(this::pressed); hit.setOnMouseReleased(this::released); hit.setOnMouseClicked(this::clicked);
            root.addEventFilter(MouseEvent.MOUSE_MOVED, e -> showBar()); root.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> showBar());
            holdTimer.setOnFinished(e -> holdStart());
            clickDelay.setOnFinished(e -> toggle(true));
            hideTimer.setOnFinished(e -> { if (bar.isHover() || dragging) restartHide(); else hideBar(); });
            mp.currentTimeProperty().addListener((o, a, n) -> tick(n));
            mp.statusProperty().addListener((o, a, n) -> { ppBtn.getChildren().set(1, n == MediaPlayer.Status.PLAYING ? pauseShape(u * 0.42) : playShape(u * 0.42)); if (n == MediaPlayer.Status.PLAYING) restartHide(); else showBar(); });
            mp.setOnReady(() -> tick(mp.getCurrentTime()));
            restartHide();
        }

        // ---- time / progress ----
        /** Length of ONE play-through. (getTotalDuration() is INDEFINITE for a looping player, which made the seek bar do nothing.) */
        double total() { Duration d = mp.getMedia() == null ? null : mp.getMedia().getDuration(); double t = d == null ? 0 : d.toSeconds(); return (t > 0 && !Double.isInfinite(t)) ? t : 0; }
        static String fmt(double s) { int t = (int) Math.max(0, s); return (t / 60) + ":" + (t % 60 < 10 ? "0" : "") + (t % 60); }
        void setTime(double cur, double tot) { time.setText(fmt(cur) + " / " + fmt(tot)); }
        void tick(Duration n) {
            double t = total(); if (t <= 0 || n == null) return; double cur = n.toSeconds();
            mini.setWidth(w * Math.min(1, cur / t));
            if (!dragging) { seek.setRatio(cur / t); setTime(cur, t); }
        }

        // ---- actions ----
        void run(String cmd) {
            switch (cmd) { case "toggle": toggle(true); break; case "back": seekBy(-5); break; case "fwd": seekBy(5); break; case "mute": toggleMute(); break; default: break; }
            showBar();
        }
        void toggle(boolean flash) {
            boolean playing = mp.getStatus() == MediaPlayer.Status.PLAYING;
            if (playing) mp.pause(); else mp.play();
            flash(playing ? pauseShape(26) : playShape(26));
        }
        void seekBy(double sec) {
            double t = total(); double cur = mp.getCurrentTime().toSeconds(); double to = cur + sec;
            to = Math.max(0, t > 0 ? Math.min(to, t - 0.1) : to); mp.seek(Duration.seconds(to));
            if (t > 0) { seek.setRatio(to / t); setTime(to, t); mini.setWidth(w * to / t); }
            Text lt = new Text(sec < 0 ? "\u00AB " + (int) -sec + "s" : (int) sec + "s \u00BB"); StackPane l = pill(lt, 22);
            StackPane.setAlignment(l, sec < 0 ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT); StackPane.setMargin(l, new Insets(0, 40, 0, 40)); pop(l, false);
        }
        void cycleSpeed() {
            double cur = mp.getRate(); int i = 0; for (int k = 0; k < SPEEDS.length; k++) if (Math.abs(SPEEDS[k] - cur) < 0.01) i = k;
            double nr = SPEEDS[(i + 1) % SPEEDS.length]; mp.setRate(nr); speedTxt.setText((nr % 1 == 0 ? String.valueOf((int) nr) : String.valueOf(nr)) + "x");
        }
        void toggleMute() {
            muted = !muted; if (!muted && volume == 0) volume = 0.6; mp.setMute(muted); mp.setVolume(volume); refreshMute();
        }
        void refreshMute() { muteBtn.getChildren().set(1, speakerShape(muted || volume == 0)); }

        // ---- mouse on the video: click, double-click, press and hold ----
        void zoneOf(MouseEvent e) { double x = e.getX(), W = Math.max(1, hit.getWidth()); zone = x < W * 0.33 ? -1 : x > W * 0.67 ? 1 : 0; }
        void pressed(MouseEvent e) { suppressClick = false; zoneOf(e); holdTimer.playFromStart(); }
        void holdStart() {
            holdActive = true;
            if (zone == 1) { savedRate = mp.getRate(); mp.setRate(2.0); badgeTxt.setText("2x  \u00BB\u00BB"); badge.setVisible(true); }
            else if (mp.getStatus() == MediaPlayer.Status.PLAYING) { pausedByHold = true; mp.pause(); }
        }
        void released(MouseEvent e) {
            holdTimer.stop(); if (!holdActive) return;
            holdActive = false; suppressClick = true; badge.setVisible(false);
            if (zone == 1) mp.setRate(savedRate); if (pausedByHold) { pausedByHold = false; mp.play(); }
        }
        void clicked(MouseEvent e) {
            if (suppressClick) { suppressClick = false; return; }
            zoneOf(e);
            if (e.getClickCount() >= 2) { clickDelay.stop(); if (zone < 0) seekBy(-10); else if (zone > 0) seekBy(10); }
            else clickDelay.playFromStart();
        }

        // ---- show / hide the control bar ----
        void showBar() { if (!barShown) { barShown = true; bar.setMouseTransparent(false); mini.setVisible(false); fade.stop(); fade.setToValue(1); fade.play(); } restartHide(); }
        void restartHide() { hideTimer.stop(); if (mp.getStatus() == MediaPlayer.Status.PLAYING) hideTimer.playFromStart(); }
        void hideBar() { barShown = false; bar.setMouseTransparent(true); mini.setVisible(true); fade.stop(); fade.setToValue(0); fade.play(); }

        // ---- little pop animation (centre icon, or the skip bubbles) ----
        void flash(Node icon) { StackPane c = new StackPane(new Circle(38, Color.rgb(0, 0, 0, 0.55)), icon); c.setMaxSize(76, 76); pop(c, true); }
        void pop(Node n, boolean grow) {
            n.setMouseTransparent(true); fb.getChildren().add(n);
            FadeTransition ft = new FadeTransition(Duration.millis(520), n); ft.setFromValue(1); ft.setToValue(0);
            ScaleTransition st = new ScaleTransition(Duration.millis(520), n); st.setFromX(grow ? 0.8 : 1); st.setFromY(grow ? 0.8 : 1); st.setToX(grow ? 1.3 : 1.1); st.setToY(grow ? 1.3 : 1.1);
            ParallelTransition pt = new ParallelTransition(ft, st); pt.setOnFinished(e -> fb.getChildren().remove(n)); pt.play();
        }

        /** A rounded dark bubble with white bold text (built from plain shapes, no javafx-controls needed). */
        static StackPane pill(Text t, double pad) {
            t.setFill(Color.WHITE); t.setFont(Font.font("System", FontWeight.BOLD, 14));
            StackPane sp = new StackPane(t); sp.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-background-radius: 30;"); sp.setPadding(new Insets(pad * 0.6, pad, pad * 0.6, pad));
            sp.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE); return sp;
        }
        // ---- icons ----
        static Node playShape(double s) { Polygon p = new Polygon(0, 0, 0, s, s * 0.86, s / 2); p.setFill(Color.WHITE); return p; }
        static Node pauseShape(double s) {
            Rectangle a = new Rectangle(s * 0.3, s), b = new Rectangle(s * 0.3, s); a.setFill(Color.WHITE); b.setFill(Color.WHITE);
            HBox hb = new HBox(s * 0.26, a, b); hb.setAlignment(Pos.CENTER); hb.setMaxSize(s * 0.86, s); return hb;
        }
        static Node speakerShape(boolean off) {
            Polygon body = new Polygon(0, 5, 4, 5, 10, 0, 10, 16, 4, 11, 0, 11); body.setFill(Color.WHITE);
            Group g = new Group(body);
            if (off) { Line l1 = new Line(13, 5, 21, 11), l2 = new Line(21, 5, 13, 11); for (Line l : new Line[]{l1, l2}) { l.setStroke(Color.WHITE); l.setStrokeWidth(2); g.getChildren().add(l); } }
            else { Arc a = new Arc(10, 8, 6, 6, -45, 90); a.setType(ArcType.OPEN); a.setFill(Color.TRANSPARENT); a.setStroke(Color.WHITE); a.setStrokeWidth(2); Arc b = new Arc(10, 8, 11, 11, -45, 90); b.setType(ArcType.OPEN); b.setFill(Color.TRANSPARENT); b.setStroke(Color.WHITE); b.setStrokeWidth(2); g.getChildren().addAll(a, b); }
            return g;
        }
        static StackPane btn(Node icon, double size, Runnable r) {
            Circle bg = new Circle(size / 2, Color.TRANSPARENT); StackPane sp = new StackPane(bg, icon); sp.setCursor(Cursor.HAND); sp.setMinSize(size, size);
            sp.setOnMouseEntered(e -> bg.setFill(Color.rgb(255, 255, 255, 0.22))); sp.setOnMouseExited(e -> bg.setFill(Color.TRANSPARENT));
            if (r != null) sp.setOnMouseClicked(e -> { r.run(); e.consume(); });
            return sp;
        }
        static StackPane textBtn(String s, double size, Runnable r) {
            Text t = new Text(s); t.setFill(Color.WHITE); t.setFont(Font.font("System", FontWeight.BOLD, size < 30 ? 10 : 12));
            StackPane sp = btn(t, size, r); Circle ring = new Circle(size / 2 - 1, Color.TRANSPARENT); ring.setStroke(Color.rgb(255, 255, 255, 0.8)); ring.setStrokeWidth(1.5); ring.setMouseTransparent(true); sp.getChildren().add(1, ring); return sp;
        }
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