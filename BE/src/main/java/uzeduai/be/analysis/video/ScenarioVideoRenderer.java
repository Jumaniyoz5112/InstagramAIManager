package uzeduai.be.analysis.video;

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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class ScenarioVideoRenderer {

    private static final int WIDTH = 720;
    private static final int HEIGHT = 1280;

    private final String ffmpegPath;

    public ScenarioVideoRenderer(@Value("${app.video.ffmpeg:}") String ffmpegPath) {
        this.ffmpegPath = ffmpegPath == null ? "" : ffmpegPath.trim();
    }

    public RenderedClip render(Map<String, Object> scenario) throws IOException, InterruptedException {
        List<Card> cards = cards(scenario);
        Path work = Files.createTempDirectory("scenario-video-");
        List<Path> frames = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            Path png = work.resolve("shot-" + i + ".png");
            ImageIO.write(draw(cards.get(i), i), "png", png.toFile());
            frames.add(png);
        }
        Path mp4 = work.resolve("scenario.mp4");
        encode(frames, cards, mp4);
        double duration = cards.stream().mapToDouble(Card::seconds).sum();
        return new RenderedClip(mp4, duration, work);
    }

    private void encode(List<Path> frames, List<Card> cards, Path output) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(ffmpeg().toString());
        command.add("-y");
        for (int i = 0; i < frames.size(); i++) {
            command.add("-loop");
            command.add("1");
            command.add("-t");
            command.add(String.format(Locale.US, "%.2f", cards.get(i).seconds()));
            command.add("-i");
            command.add(frames.get(i).toString());
        }
        StringBuilder filter = new StringBuilder();
        for (int i = 0; i < frames.size(); i++) {
            filter.append('[').append(i).append(":v]scale=")
                    .append(WIDTH).append(':').append(HEIGHT)
                    .append(",setsar=1,fps=30[v").append(i).append("];");
        }
        for (int i = 0; i < frames.size(); i++) {
            filter.append("[v").append(i).append(']');
        }
        filter.append("concat=n=").append(frames.size()).append(":v=1:a=0,format=yuv420p[v]");
        command.add("-filter_complex");
        command.add(filter.toString());
        command.add("-map");
        command.add("[v]");
        command.add("-c:v");
        command.add("libx264");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-movflags");
        command.add("+faststart");
        command.add(output.toString());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        byte[] log = process.getInputStream().readAllBytes();
        if (!process.waitFor(90, TimeUnit.SECONDS)) {
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
            cards.add(new Card("HOOK", title.isBlank() ? "Story" : title, hook, "", 2.2));
        }
        Object shots = scenario.get("shots");
        if (shots instanceof List<?> list) {
            int index = 1;
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
                String purpose = text(raw.get("purpose"));
                String headline = overlay.isBlank() ? visual : overlay;
                String body = overlay.isBlank() ? note : visual;
                cards.add(new Card(index + " · " + purpose.toUpperCase(Locale.ROOT), headline, body, note, seconds));
                index++;
            }
        }
        if (cards.isEmpty()) {
            String caption = text(scenario.get("captionPrimaryUz"));
            cards.add(new Card("STORY", title.isBlank() ? "Ssenariya" : title, caption, hook, 6));
        }
        return cards;
    }

    private static BufferedImage draw(Card card, int index) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color[] backgrounds = {
                new Color(0x0b1626),
                new Color(0x102033),
                new Color(0x12182b),
                new Color(0x0e1c28)
        };
        g.setColor(backgrounds[index % backgrounds.length]);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(new Color(0x00c2a8));
        g.fillRect(0, 0, WIDTH, 14);

        int y = 160;
        g.setFont(uiFont(Font.BOLD, 22));
        g.setColor(new Color(0x8ee8dc));
        y = drawBlock(g, card.kicker().isBlank() ? "STORY" : card.kicker(), 64, y, WIDTH - 128, 28);

        y += 36;
        g.setFont(uiFont(Font.BOLD, 48));
        g.setColor(Color.WHITE);
        y = drawBlock(g, card.title().isBlank() ? " " : card.title(), 64, y, WIDTH - 128, 58);

        y += 40;
        g.setFont(uiFont(Font.PLAIN, 30));
        g.setColor(new Color(0xd5deea));
        drawBlock(g, card.body(), 64, y, WIDTH - 128, 40);

        if (!card.footer().isBlank() && !card.footer().equals(card.body())) {
            g.setFont(uiFont(Font.PLAIN, 24));
            g.setColor(new Color(0x9aa8ba));
            drawBlock(g, card.footer(), 64, 1040, WIDTH - 128, 34);
        }
        g.dispose();
        return image;
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

    private record Card(String kicker, String title, String body, String footer, double seconds) {
    }

    public record RenderedClip(Path file, double durationSec, Path workDir) {
    }
}
