import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 生成 ChestSeeker 的 400x400 项目图标（CurseForge 要求 1:1 原图 PNG）。
 *
 * 运行方式（无需先编译，Java 11+ 支持直接跑单文件）：
 *   java -Djava.awt.headless=true LogoGen.java
 *
 * 输出：logo-400x400.png（生成在同目录）
 */
public final class LogoGen {

    private static final int S = 400;

    public static void main(String[] args) throws Exception {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        Color bg = new Color(0x1B1B21);
        Color outline = new Color(0x3E2410);
        Color lidWood = new Color(0xC08A52);
        Color bodyWood = new Color(0x9A6438);
        Color green = new Color(0x62C462);
        Color greenDark = new Color(0x2F6B2F);
        Color black = new Color(0x121A10);
        Color brass = new Color(0xE9C55C);
        Color brassDark = new Color(0x6B4A10);

        // 背景
        g.setColor(bg);
        g.fillRoundRect(0, 0, S, S, 64, 64);

        // 箱子：盖子
        g.setColor(lidWood);
        g.fillRoundRect(44, 132, 312, 116, 26, 26);
        g.setColor(outline);
        g.setStroke(new BasicStroke(8f));
        g.drawRoundRect(44, 132, 312, 116, 26, 26);

        // 箱子：箱体（略窄一点，形成盖沿）
        g.setColor(bodyWood);
        g.fillRoundRect(52, 230, 296, 122, 20, 20);
        g.setColor(outline);
        g.setStroke(new BasicStroke(8f));
        g.drawRoundRect(52, 230, 296, 122, 20, 20);

        // 盖子与箱体之间的暗缝
        g.setColor(outline);
        g.setStroke(new BasicStroke(6f));
        g.drawLine(44, 226, 356, 226);

        // 搭扣
        g.setColor(brass);
        g.fillRoundRect(176, 202, 48, 56, 10, 10);
        g.setColor(brassDark);
        g.setStroke(new BasicStroke(6f));
        g.drawRoundRect(176, 202, 48, 56, 10, 10);
        g.setColor(brassDark);
        g.fillOval(190, 222, 20, 20);

        // 箱体上的苦力怕脸
        g.setColor(green);
        g.fillRoundRect(112, 248, 176, 96, 16, 16);
        g.setColor(greenDark);
        g.setStroke(new BasicStroke(6f));
        g.drawRoundRect(112, 248, 176, 96, 16, 16);

        g.setColor(black);
        // 双眼
        g.fillRect(142, 266, 34, 34);
        g.fillRect(224, 266, 34, 34);
        // 嘴：中央横条 + 两条下齿
        g.fillRect(178, 292, 44, 20);
        g.fillRect(150, 308, 34, 30);
        g.fillRect(216, 308, 34, 30);

        g.dispose();
        File out = new File("logo-400x400.png");
        ImageIO.write(img, "png", out);
        System.out.println("written: " + out.getAbsolutePath());
    }

    private LogoGen() {
    }
}
