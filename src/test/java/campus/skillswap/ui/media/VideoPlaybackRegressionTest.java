package campus.skillswap.ui.media;

import campus.skillswap.model.Post;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Small dependency-free regression checks for the video upload/playback flow.
 * Run with: mvn -q -DskipTests test-compile
 *           java -ea -cp "target/classes;target/test-classes" campus.skillswap.ui.media.VideoPlaybackRegressionTest
 */
public class VideoPlaybackRegressionTest {
    static int checks;

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        cacheKeysFollowTheVideoRevision();
        largeVideosOfferTapToPlay();
        downloadErrorsAreUseful();
        preparationErrorsAreActionable();
        ffmpegTimeoutStopsAHungProcess();
        conversionProducesAPlayableMp4();
        System.out.println("VideoPlaybackRegressionTest: " + checks + " checks passed");
    }

    /** Replacing a video with the same name/size must not reuse an old download, conversion, feed copy or thumbnail. */
    static void cacheKeysFollowTheVideoRevision() {
        Post p = new Post();
        p.id = 7; p.videoName = "clip 1.mp4"; p.videoSize = 5_000; p.videoStamp = 100;

        Path raw = Videos.videoCacheFile(p);
        Path play = Videos.playableCacheFile(p);
        Path repaired = Videos.repairedCacheFile(p);
        Path feed = Videos.feedCopyFile(p);
        String thumb = Videos.thumbKey(p);

        p.videoStamp = 200;
        check(!raw.equals(Videos.videoCacheFile(p)), "raw download cache key must change after a replacement");
        check(!play.equals(Videos.playableCacheFile(p)), "converted playback cache key must change after a replacement");
        check(!repaired.equals(Videos.repairedCacheFile(p)), "repaired playback cache key must change after a replacement");
        check(!feed.equals(Videos.feedCopyFile(p)), "feed copy cache key must change after a replacement");
        check(!thumb.equals(Videos.thumbKey(p)), "thumbnail cache key must change after a replacement");
    }

    static void largeVideosOfferTapToPlay() {
        Post p = new Post();
        p.videoSize = Videos.AUTOPLAY_MAX;
        check(!Videos.needsTapToPlay(p), "a video at the autoplay limit may still autoplay");
        check(!Videos.feedTileCaption(p).toLowerCase().contains("large"), "normal videos keep the existing caption");

        p.videoSize = Videos.AUTOPLAY_MAX + 1;
        check(Videos.needsTapToPlay(p), "a video over the autoplay limit must be marked for tap-to-play");
        check(Videos.feedTileCaption(p).toLowerCase().contains("large"), "large-video caption must explain why it did not autoplay");
    }

    static void downloadErrorsAreUseful() {
        String timeout = Videos.downloadErrorMessage(new java.net.SocketTimeoutException("Read timed out")).toLowerCase();
        check(timeout.contains("timed out"), "timeouts must be named in download errors");
        check(timeout.contains("try again"), "download errors must suggest what to do next");

        String missing = Videos.downloadErrorMessage(new IOException("Video not found on the server"));
        check(missing.contains("Video not found on the server"), "specific download errors must be preserved");
    }

    static void preparationErrorsAreActionable() {
        String m = Videos.prepareErrorMessage(new IOException("ffmpeg was not found"));
        check(m.contains("Could not prepare this video"), "preparation failures must be reported clearly");
        check(m.contains("ffmpeg was not found"), "the underlying conversion error must be kept");
        check(m.contains("H.264") && m.contains("AAC"), "preparation errors must tell the person how to make a compatible file");
    }

    /** The conversion watchdog must not leave a stuck ffmpeg (or test process) running forever. */
    static void ffmpegTimeoutStopsAHungProcess() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        List<String> cmd = new ArrayList<>(Arrays.asList(java, "-cp", System.getProperty("java.class.path"), SleepMain.class.getName()));
        boolean timedOut = false;
        try {
            Videos.runFfmpeg(cmd, pct -> { }, 500);
        } catch (IOException e) {
            timedOut = e.getMessage() != null && e.getMessage().toLowerCase().contains("did not finish");
        }
        check(timedOut, "a hung conversion must fail with a timeout message");
    }

    /** Optional: needs ffmpeg on PATH. Verifies the upload converter really produces H.264 + AAC in MP4. */
    static void conversionProducesAPlayableMp4() throws Exception {
        String ff = Videos.ffmpeg();
        if (ff == null) { System.out.println("SKIP conversionProducesAPlayableMp4: ffmpeg is not available"); return; }

        Path dir = Files.createTempDirectory("skillswap-video-test");
        Path input = dir.resolve("incompatible.mkv");
        File converted = null;
        try {
            Process make = new ProcessBuilder(ff, "-y",
                    "-f", "lavfi", "-i", "testsrc=size=64x48:rate=10",
                    "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100",
                    "-t", "1", "-c:v", "mpeg4", "-c:a", "pcm_s16le", input.toString()).redirectErrorStream(true).start();
            String makeOut = new String(make.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            check(make.waitFor() == 0 && Files.size(input) > 0, "could not create test video: " + makeOut);

            converted = Videos.convertForApp(input.toFile(), pct -> { });
            String[] info = Videos.probe(converted);
            check(converted.getName().toLowerCase().endsWith(".mp4"), "converted upload must be an MP4");
            check(Videos.playerCompatible(info),
                    "converted upload must be H.264 yuv420p with AAC: " + Videos.diagnose(converted));
            check("aac".equals(info[2]), "converted upload must have AAC audio: " + Videos.diagnose(converted));
            check(converted.length() <= Videos.MAX_VIDEO, "converted upload must stay within the size limit");
        } finally {
            if (converted != null) Files.deleteIfExists(converted.toPath());
            Files.deleteIfExists(input);
            Files.deleteIfExists(dir);
        }
    }

    public static class SleepMain {
        public static void main(String[] args) throws Exception { Thread.sleep(10_000); }
    }
}
