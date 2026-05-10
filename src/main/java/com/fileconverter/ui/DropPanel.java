package com.fileconverter.ui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

public class DropPanel extends JPanel {
    private final Color borderColor = new Color(100, 149, 237);
    private final Color hoverColor = new Color(65, 105, 225);
    private final Color bgColor = new Color(245, 245, 250);
    private final Color hoverBg = new Color(230, 240, 255);

    private boolean hover = false;
    private String placeholder;
    private Color currentBorder = borderColor;
    private Color currentBg = bgColor;
    private Timer animTimer;

    public DropPanel(String placeholder) {
        this.placeholder = placeholder;
        setOpaque(true);
        setBackground(bgColor);
        setBorder(new CompoundBorder(
                new DashedBorder(borderColor, 2, 8, 4),
                new EmptyBorder(30, 30, 30, 30)
        ));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        animTimer = new Timer(50, e -> repaint());
    }

    public void setHover(boolean h) {
        this.hover = h;
        currentBorder = h ? hoverColor : borderColor;
        currentBg = h ? hoverBg : bgColor;
        setBackground(currentBg);
        setBorder(new CompoundBorder(
                new DashedBorder(currentBorder, 2, 8, 4),
                new EmptyBorder(30, 30, 30, 30)
        ));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Icon
        g2.setColor(new Color(120, 140, 180));
        int cx = getWidth() / 2;
        int cy = getHeight() / 2 - 15;
        g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Upload arrow
        int arrowY = cy - 25;
        g2.drawLine(cx, arrowY + 30, cx, arrowY);
        int[] xs = {cx - 8, cx, cx + 8};
        int[] ys = {arrowY + 8, arrowY, arrowY + 8};
        g2.fillPolygon(xs, ys, 3);
        // Document icon
        g2.drawRoundRect(cx - 20, cy + 5, 40, 48, 6, 6);
        for (int i = 0; i < 4; i++) {
            g2.drawLine(cx - 12, cy + 18 + i * 9, cx + 12, cy + 18 + i * 9);
        }

        // Text
        g2.setColor(new Color(80, 80, 100));
        g2.setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
        String text = hover ? "松开文件开始转换" : placeholder;
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, cx - fm.stringWidth(text) / 2, cy + 80);

        g2.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        g2.setColor(new Color(140, 150, 170));
        String hint = "支持拖拽或点击选择文件";
        fm = g2.getFontMetrics();
        g2.drawString(hint, cx - fm.stringWidth(hint) / 2, cy + 100);

        g2.dispose();
    }

    static class DashedBorder extends AbstractBorder {
        private final Color color;
        private final int thickness;
        private final int dashLength;
        private final int gap;

        DashedBorder(Color color, int thickness, int dashLength, int gap) {
            this.color = color;
            this.thickness = thickness;
            this.dashLength = dashLength;
            this.gap = gap;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                    0, new float[]{dashLength, gap}, 0));
            g2.drawRoundRect(x + thickness / 2, y + thickness / 2,
                    w - thickness, h - thickness, 16, 16);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness + 4, thickness + 4, thickness + 4, thickness + 4);
        }
    }
}
