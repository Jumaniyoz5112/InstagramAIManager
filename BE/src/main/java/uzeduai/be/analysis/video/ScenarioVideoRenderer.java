package uzeduai.be.analysis.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class ScenarioVideoRenderer {

    private static final Logger log = LoggerFactory.getLogger(ScenarioVideoRenderer.class);
    private static final int WIDTH = 720;
    private static final int HEIGHT = 1280;

    private final String ffmpegPath;
    private final ShotImageClient images;

    public ScenarioVideoRenderer(
            @Value("${app.video.ffmpeg:}") String ffmpegPath,
            ShotImageClient images
    ) {
        this.ffmpegPath = ffmpegPath == null ? "" : ffmpegPath.trim();
        this.images = images;
    }

    public RenderedClip render(Map<String, Object> scenario) throws IOException, InterruptedException {
        List<Card> cards = cards(scenario);
        Path work = Files.createTempDirectory("scenario-video-");
        List<Path> parts = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            log.info("Scenario shot {} of {}", i + 1, cards.size());
            BufferedImage photo = loadPhoto(card, i);
            Path png = work.resolve("shot-" + i + ".png");
            ImageIO.write(compose(photo, card.overlay()), "png", png.toFile());
            Path part = work.resolve("part-" + i + ".mp4");
            encodeClip(png, card.seconds(), part);
            parts.add(part);
        }
        Path mp4 = work.resolve("scenario.mp4");
        concat(parts, mp4);
        double duration = cards.stream().mapToDouble(Card::seconds).sum();
        return new RenderedClip(mp4, duration, work);
    }

    private BufferedImage loadPhoto(Card card, int index) {
        IllegalStateException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return images.fetch(card.scene(), images.seedFor(card.scene(), index + attempt * 17));
            } catch (IllegalStateException ex) {
                last = ex;
            }
        }
        throw last == null ? new IllegalStateException("image API failed") : last;
    }

    private void encodeClip(Path png, double seconds, Path output) throws IOException, InterruptedException {
        int frames = Math.max(36, (int) Math.round(seconds * 30));
        String filter = "zoompan=z='min(zoom+0.0012,1.08)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d="
                + frames + ":s=" + WIDTH + "x" + HEIGHT + ":fps=30";
        run(output, List.of(
                ffmpeg().toString(), "-y",
                "-loop", "1",
                "-i", png.toString(),
                "-vf", filter,
                "-frames:v", Integer.toString(frames),
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                "-r", "30",
                output.toString()
        ), 90);
    }

    private void concat(List<Path> parts, Path output) throws IOException, InterruptedException {
        Path list = output.resolveSibling("list.txt");
        StringBuilder body = new StringBuilder();
        for (Path part : parts) {
            body.append("file '").append(part.toAbsolutePath().toString().replace('\\', '/')).append("'\n");
        }
        Files.writeString(list, body.toString(), StandardCharsets.UTF_8);
        run(output, List.of(
                ffmpeg().toString(), "-y",
                "-f", "concat",
                "-safe", "0",
                "-i", list.toString(),
                "-c", "copy",
                "-movflags", "+faststart",
                output.toString()
        ), 60);
    }

    private static void run(Path output, List<String> command, int timeoutSec) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        byte[] log = process.getInputStream().readAllBytes();
        if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("ffmpeg timed out");
        }
        if (process.exitValue() != 0 || !Files.exists(output)) {
            String detail = new String(log, StandardCharsets.UTF_8);
            int start = Math.max(0, detail.length() - 400);
            throw new IllegalStateException("ffmpeg failed: " + detail.substring(start).replaceAll("\\s+", " ").trim());
        }
    }

    private Path ffmpeg() {
        if (!ffmpegPath.isBlank() && Files.isRegularFile(Path.of(ffmpegPath))) {
            return Path.of(ffmpegPath);
        }
        String env = System.getenv("FFMPEG_PATH");
        if (env != null && !env.isBlank() && Files.isRegularFile(Path.of(env.trim()))) {
            return Path.of(env.trim());
        }
        Path local = Path.of("tools", "ffmpeg", "ffmpeg.exe");
        if (Files.isRegularFile(local)) {
            return local.toAbsolutePath();
        }
        return Path.of("ffmpeg");
    }

    private static List<Card> cards(Map<String, Object> scenario) {
        List<Card> cards = new ArrayList<>();
        String title = text(scenario.get("titleUz"));
        String hook = text(scenario.get("hookUz"));
        if (!title.isBlank() || !hook.isBlank()) {
            String scene = (title + ". " + hook).trim();
            cards.add(new Card(scene, hook.isBlank() ? title : hook, 2.4));
        }
        Object shots = scenario.get("shots");
        if (shots instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                double start = number(raw.get("startSec"));
                double end = number(raw.get("endSec"));
                double seconds = end > start ? Math.min(6, Math.max(1.4, end - start)) : 2.0;
                String overlay = text(raw.get("overlayTextUz"));
                String visual = text(raw.get("visualUz"));
                String note = text(raw.get("noteUz"));
                String scene = !visual.isBlank() ? visual : (!overlay.isBlank() ? overlay : note);
                String caption = !overlay.isBlank() ? overlay : "";
                if (!scene.isBlank()) {
                    cards.add(new Card(scene, caption, seconds));
                }
            }
        }
        if (cards.isEmpty()) {
            String caption = text(scenario.get("captionPrimaryUz"));
            cards.add(new Card(title.isBlank() ? caption : title, hook.isBlank() ? caption : hook, 6));
        }
        return cards;
    }

    private static BufferedImage compose(BufferedImage source, String overlay) {
        BufferedImage frame = cover(source);
        if (overlay == null || overlay.isBlank()) {
            return frame;
        }
        Graphics2D g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int band = 280;
        for (int i = 0; i < band; i++) {
            int alpha = (int) (190 * (i / (double) band));
            g.setColor(new Color(0, 0, 0, alpha));
            g.drawLine(0, HEIGHT - band + i, WIDTH, HEIGHT - band + i);
        }
        g.setFont(uiFont(Font.BOLD, 34));
        g.setColor(Color.WHITE);
        List<String> lines = wrap(overlay, g.getFontMetrics(), WIDTH - 96);
        int shown = Math.min(3, lines.size());
        int y = HEIGHT - 56 - (shown - 1) * 42;
        for (int i = 0; i < shown; i++) {
            String line = lines.get(i);
            int x = Math.max(40, (WIDTH - g.getFontMetrics().stringWidth(line)) / 2);
            g.drawString(line, x, y);
            y += 42;
        }
        g.dispose();
        return frame;
    }

    private static BufferedImage cover(BufferedImage source) {
        double scale = Math.max(WIDTH / (double) source.getWidth(), HEIGHT / (double) source.getHeight());
        int w = Math.max(WIDTH, (int) Math.round(source.getWidth() * scale));
        int h = Math.max(HEIGHT, (int) Math.round(source.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, w, h, null);
        g.dispose();
        int x = Math.max(0, (w - WIDTH) / 2);
        int y = Math.max(0, (h - HEIGHT) / 2);
        BufferedImage crop = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D out = crop.createGraphics();
        out.drawImage(scaled, 0, 0, WIDTH, HEIGHT, x, y, x + WIDTH, y + HEIGHT, null);
        out.dispose();
        return crop;
    }

    private static int drawBlock(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        if (text == null || text.isBlank()) {
            return y;
        }
        FontMetrics metrics = g.getFontMetrics();
        int lines = 0;
        for (String line : wrap(text, metrics, maxWidth)) {
            if (lines >= 8) {
                break;
            }
            g.drawString(line, x, y);
            y += lineHeight;
            lines++;
        }
        return y;
    }

    private static List<String> wrap(String text, FontMetrics metrics, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.replace('\n', ' ').split("\\s+")) {
            if (word.isBlank()) {
                continue;
            }
            String next = current.isEmpty() ? word : current + " " + word;
            if (metrics.stringWidth(next) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(next);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static Font uiFont(int style, int size) {
        Font font = new Font("Segoe UI", style, size);
        if ("Dialog".equalsIgnoreCase(font.getFamily())) {
            return new Font(Font.SANS_SERIF, style, size);
        }
        return font;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private record Card(String scene, String overlay, double seconds) {
    }

    public record RenderedClip(Path file, double durationSec, Path workDir) {
    }
}
