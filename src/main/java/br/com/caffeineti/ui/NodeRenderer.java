package br.com.caffeineti.ui;

import br.com.caffeineti.model.*;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateless utility that draws FlowNode shapes and transitions on a Graphics2D context.
 *
 * Transitions use orthogonal (right-angle) routing so they are always straight lines
 * and easy to click.
 */
public class NodeRenderer {

    // Palette of colors per type
    static final Color C_START      = new Color(0x43A047);
    static final Color C_END        = new Color(0xE53935);
    static final Color C_TASK       = new Color(0x1E88E5);
    static final Color C_STATE      = new Color(0x00ACC1);
    static final Color C_DECISION   = new Color(0xFB8C00);
    static final Color C_FORK_JOIN  = new Color(0x455A64);
    static final Color C_ACTION     = new Color(0x8E24AA);
    static final Color C_TEXT       = Color.WHITE;
    static final Color C_BORDER_SEL = new Color(0xFFD600);
    static final Color C_PORT       = new Color(0x43A047);
    static final Color C_TRANSITION = new Color(0x546E7A);

    private static final int PORT_R = 5;
    private static final int ARC    = 12;

    // =========================================================================
    // Node drawing
    // =========================================================================

    public static void draw(Graphics2D g, FlowNode node, boolean selected, boolean showPorts) {
        Rectangle b = node.getBounds();
        NodeType type = node.getType();

        // Drop shadow
        g.setColor(new Color(0, 0, 0, 35));
        g.fillRoundRect(b.x + 3, b.y + 3, b.width, b.height, ARC, ARC);

        switch (type) {
            case START_STATE -> drawCircle(g, b, C_START, selected);
            case END_STATE   -> drawEndCircle(g, b, C_END, selected);
            case TASK_NODE   -> drawRect(g, b, C_TASK, selected);
            case STATE       -> drawRect(g, b, C_STATE, selected);
            case DECISION    -> drawDiamond(g, b, C_DECISION, selected);
            case FORK, JOIN  -> drawBar(g, b, C_FORK_JOIN, selected);
            case ACTION_NODE -> drawRect(g, b, C_ACTION, selected);
        }

        drawLabel(g, node, b);

        if (showPorts) {
            for (Point p : node.getPorts()) {
                g.setColor(C_PORT);
                g.fillOval(p.x - PORT_R, p.y - PORT_R, PORT_R * 2, PORT_R * 2);
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(1.2f));
                g.drawOval(p.x - PORT_R, p.y - PORT_R, PORT_R * 2, PORT_R * 2);
            }
        }
    }

    private static void drawCircle(Graphics2D g, Rectangle b, Color fill, boolean selected) {
        g.setColor(fill);
        g.fillOval(b.x, b.y, b.width, b.height);
        g.setColor(selected ? C_BORDER_SEL : fill.darker());
        g.setStroke(new BasicStroke(selected ? 3f : 1.5f));
        g.drawOval(b.x, b.y, b.width, b.height);
    }

    private static void drawEndCircle(Graphics2D g, Rectangle b, Color fill, boolean selected) {
        g.setColor(fill);
        g.fillOval(b.x, b.y, b.width, b.height);
        g.setColor(selected ? C_BORDER_SEL : fill.darker());
        g.setStroke(new BasicStroke(selected ? 3f : 2f));
        g.drawOval(b.x, b.y, b.width, b.height);
        int inset = 8;
        g.setColor(Color.WHITE);
        g.fillOval(b.x + inset, b.y + inset, b.width - inset * 2, b.height - inset * 2);
        g.setColor(fill);
        g.fillOval(b.x + inset + 4, b.y + inset + 4,
                b.width - (inset + 4) * 2, b.height - (inset + 4) * 2);
    }

    private static void drawRect(Graphics2D g, Rectangle b, Color fill, boolean selected) {
        GradientPaint gp = new GradientPaint(b.x, b.y, fill.brighter(), b.x, b.y + b.height, fill.darker());
        g.setPaint(gp);
        g.fillRoundRect(b.x, b.y, b.width, b.height, ARC, ARC);
        g.setPaint(null);
        g.setColor(selected ? C_BORDER_SEL : fill.darker().darker());
        g.setStroke(new BasicStroke(selected ? 2.5f : 1.5f));
        g.drawRoundRect(b.x, b.y, b.width, b.height, ARC, ARC);
    }

    private static void drawDiamond(Graphics2D g, Rectangle b, Color fill, boolean selected) {
        int cx = b.x + b.width / 2;
        int cy = b.y + b.height / 2;
        Polygon diamond = new Polygon(
            new int[]{cx, b.x + b.width, cx, b.x},
            new int[]{b.y, cy, b.y + b.height, cy},
            4);
        GradientPaint gp = new GradientPaint(b.x, b.y, fill.brighter(), b.x, b.y + b.height, fill.darker());
        g.setPaint(gp);
        g.fillPolygon(diamond);
        g.setPaint(null);
        g.setColor(selected ? C_BORDER_SEL : fill.darker().darker());
        g.setStroke(new BasicStroke(selected ? 2.5f : 1.5f));
        g.drawPolygon(diamond);
    }

    private static void drawBar(Graphics2D g, Rectangle b, Color fill, boolean selected) {
        g.setColor(fill);
        g.fillRect(b.x, b.y, b.width, b.height);
        g.setColor(selected ? C_BORDER_SEL : fill.darker());
        g.setStroke(new BasicStroke(selected ? 2.5f : 1.5f));
        g.drawRect(b.x, b.y, b.width, b.height);
    }

    private static void drawLabel(Graphics2D g, FlowNode node, Rectangle b) {
        NodeType type = node.getType();
        if (type == NodeType.FORK || type == NodeType.JOIN) {
            g.setColor(Color.DARK_GRAY);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            FontMetrics fm = g.getFontMetrics();
            String label = node.getName();
            g.drawString(label, b.x + (b.width - fm.stringWidth(label)) / 2, b.y - 5);
        } else {
            g.setColor(C_TEXT);
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            FontMetrics fm = g.getFontMetrics();
            String label = node.getName();
            int maxW = b.width - 10;
            if (fm.stringWidth(label) <= maxW) {
                int lw = fm.stringWidth(label);
                g.drawString(label, b.x + (b.width - lw) / 2,
                        b.y + b.height / 2 + fm.getAscent() / 2 - 2);
            } else {
                String[] words = label.split(" ");
                String line1 = "", line2 = "";
                for (String w : words) {
                    String cand = line1.isBlank() ? w : line1 + " " + w;
                    if (fm.stringWidth(cand) <= maxW) line1 = cand;
                    else line2 = line2.isBlank() ? w : line2 + " " + w;
                }
                int lh = fm.getHeight();
                g.drawString(line1,
                        b.x + (b.width - fm.stringWidth(line1)) / 2,
                        b.y + b.height / 2 - lh / 2 + fm.getAscent() - 2);
                if (!line2.isBlank())
                    g.drawString(line2,
                            b.x + (b.width - fm.stringWidth(line2)) / 2,
                            b.y + b.height / 2 + lh / 2 + fm.getAscent() - 4);
            }
        }
    }

    // =========================================================================
    // Transition routing (orthogonal — right-angle segments only)
    // =========================================================================

    /**
     * Computes the polyline waypoints for a transition.
     * All segments are horizontal or vertical (orthogonal routing).
     * This list is used for both drawing AND hit-testing so they are always consistent.
     */
    public static List<Point> routeTransition(Transition t) {
        FlowNode src = t.getSource();
        FlowNode dst = t.getTarget();
        List<Point> pts = new ArrayList<>();

        Point from = src.getConnectionPointToward(dst.getCenter());
        Point to   = dst.getConnectionPointToward(src.getCenter());

        pts.add(from);

        // Self-loop
        if (src == dst) {
            Rectangle b = src.getBounds();
            int rx = b.x + b.width + 45;
            int ry = b.y - 35;
            pts.add(new Point(rx, from.y));
            pts.add(new Point(rx, ry));
            pts.add(new Point(b.x + b.width / 2, ry));
            pts.add(to);
            return pts;
        }

        int dx = to.x - from.x;
        int dy = to.y - from.y;

        // Nearly horizontal or vertical: draw straight
        if (Math.abs(dy) < 15) {
            pts.add(to);
            return pts;
        }
        if (Math.abs(dx) < 15) {
            pts.add(to);
            return pts;
        }

        // Z-shaped orthogonal routing
        // Horizontal-first when wider than tall, vertical-first otherwise
        if (Math.abs(dx) >= Math.abs(dy)) {
            int midX = (from.x + to.x) / 2;
            pts.add(new Point(midX, from.y));
            pts.add(new Point(midX, to.y));
        } else {
            int midY = (from.y + to.y) / 2;
            pts.add(new Point(from.x, midY));
            pts.add(new Point(to.x,  midY));
        }
        pts.add(to);
        return pts;
    }

    // =========================================================================
    // Transition drawing
    // =========================================================================

    public static void drawTransition(Graphics2D g, Transition t, boolean selected) {
        List<Point> pts = routeTransition(t);
        if (pts.size() < 2) return;

        Color lineColor = selected ? C_BORDER_SEL : C_TRANSITION;
        g.setColor(lineColor);
        g.setStroke(new BasicStroke(selected ? 2.5f : 1.8f,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        for (int i = 0; i < pts.size() - 1; i++) {
            Point a = pts.get(i), b = pts.get(i + 1);
            g.drawLine(a.x, a.y, b.x, b.y);
        }

        // Arrowhead at last segment
        Point tip  = pts.get(pts.size() - 1);
        Point prev = pts.get(pts.size() - 2);
        drawArrowHead(g, prev.x, prev.y, tip.x, tip.y, lineColor);

        // Transition name label at midpoint of the middle segment
        String label = t.getName();
        if (label != null && !label.isBlank()) {
            int mid = pts.size() / 2;
            Point lp = pts.get(mid);
            g.setFont(new Font("SansSerif", Font.ITALIC, 10));
            FontMetrics fm = g.getFontMetrics();
            int lw = fm.stringWidth(label);
            int lx = lp.x - lw / 2 - 3;
            int ly = lp.y - fm.getAscent() - 2;
            g.setColor(new Color(255, 255, 255, 210));
            g.fillRoundRect(lx, ly, lw + 6, fm.getHeight() + 2, 4, 4);
            g.setColor(C_TRANSITION);
            g.drawString(label, lx + 3, ly + fm.getAscent());
        }
    }

    private static void drawArrowHead(Graphics2D g, int fromX, int fromY,
                                       int toX, int toY, Color color) {
        double angle = Math.atan2(toY - fromY, toX - fromX);
        int size = 10;
        int x1 = (int)(toX - size * Math.cos(angle - 0.42));
        int y1 = (int)(toY - size * Math.sin(angle - 0.42));
        int x2 = (int)(toX - size * Math.cos(angle + 0.42));
        int y2 = (int)(toY - size * Math.sin(angle + 0.42));
        g.setColor(color);
        g.setStroke(new BasicStroke(1f));
        g.fillPolygon(new int[]{toX, x1, x2}, new int[]{toY, y1, y2}, 3);
    }

    /** Rubber-band line while dragging a new transition. */
    public static void drawPendingTransition(Graphics2D g, Point from, Point to) {
        g.setColor(new Color(0xF9A825));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1f, new float[]{6f, 4f}, 0f));
        g.drawLine(from.x, from.y, to.x, to.y);
    }
}
