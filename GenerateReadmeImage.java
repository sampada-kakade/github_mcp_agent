import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class GenerateReadmeImage {
    public static void main(String[] args) throws IOException {
        String[] lines = {
            "[2026-07-23T12:34:56+00:00] INFO: Prepared request body:",
            "    query=Hello Copilot&repo=my-repo",
            "[2026-07-23T12:34:56+00:00] INFO: Attempt 1 of 3: POST http://localhost:8080/run",
            "[2026-07-23T12:34:56+00:00] INFO: Received response code: 200",
            "[2026-07-23T12:34:56+00:00] INFO: Elapsed time: 120 ms",
            "[2026-07-23T12:34:56+00:00] INFO: Saved response to response_20260723T123456789.json",
            "[2026-07-23T12:34:56+00:00] INFO: Request succeeded."
        };

        int padding = 20;
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 16);
        BufferedImage temp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tempG = temp.createGraphics();
        tempG.setFont(font);
        FontMetrics metrics = tempG.getFontMetrics(font);

        int width = 0;
        for (String line : lines) {
            width = Math.max(width, metrics.stringWidth(line));
        }
        int lineHeight = metrics.getHeight();
        int height = lineHeight * lines.length + padding * 2;
        tempG.dispose();

        BufferedImage image = new BufferedImage(width + padding * 2, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(30, 30, 30));
        g.fillRect(0, 0, width + padding * 2, height);
        g.setFont(font);
        g.setColor(new Color(212, 212, 212));

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains("WARN:")) {
                g.setColor(new Color(220, 220, 170));
            } else if (line.contains("ERROR:")) {
                g.setColor(new Color(244, 135, 113));
            } else if (line.contains("INFO:")) {
                g.setColor(new Color(86, 156, 214));
            } else {
                g.setColor(new Color(212, 212, 212));
            }
            g.drawString(line, padding, padding + metrics.getAscent() + i * lineHeight);
        }
        g.dispose();

        File output = new File("README-output.png");
        ImageIO.write(image, "png", output);
        System.out.println(output.getAbsolutePath());
    }
}
