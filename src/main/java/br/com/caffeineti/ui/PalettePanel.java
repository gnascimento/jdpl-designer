package br.com.caffeineti.ui;

import br.com.caffeineti.model.NodeType;
import br.com.caffeineti.i18n.Messages;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/** Left-side palette panel showing draggable node types. */
public class PalettePanel extends JPanel {

    public interface PaletteListener {
        void onNodeTypeSelected(NodeType type);
    }

    private final PaletteListener listener;
    private NodeType activeType = null;
    private final ButtonGroup buttonGroup = new ButtonGroup();

    public PalettePanel(PaletteListener listener) {
        this.listener = listener;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(8, 4, 8, 4));
        setBackground(new Color(0xECEFF1));
        setPreferredSize(new Dimension(148, 0));

        add(sectionLabel(Messages.get("palette.nodes")));
        add(Box.createRigidArea(new Dimension(0, 4)));

        for (NodeType type : NodeType.values()) {
            add(createPaletteButton(type));
            add(Box.createRigidArea(new Dimension(0, 4)));
        }

        add(Box.createVerticalGlue());
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 10));
        l.setForeground(new Color(0x90A4AE));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 4, 0, 0));
        return l;
    }

    private JToggleButton createPaletteButton(NodeType type) {
        JToggleButton btn = new JToggleButton(type.getDisplayName()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (isSelected()) {
                    g2.setColor(new Color(0xB3E5FC));
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g2.setColor(new Color(0x0288D1));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(0xE3F2FD));
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                }
                super.paintComponent(g);
            }
        };

        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btn.setIcon(createNodeIcon(type));
        btn.setIconTextGap(8);
        btn.setMaximumSize(new Dimension(140, 36));
        btn.setPreferredSize(new Dimension(140, 36));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.getModel().setRollover(true);

        buttonGroup.add(btn);

        btn.addActionListener(e -> {
            if (btn.isSelected()) {
                activeType = type;
                listener.onNodeTypeSelected(type);
            } else {
                activeType = null;
                listener.onNodeTypeSelected(null);
            }
        });

        return btn;
    }

    public void clearSelection() {
        buttonGroup.clearSelection();
        activeType = null;
    }

    private Icon createNodeIcon(NodeType type) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color color = getNodeColor(type);

                switch (type) {
                    case START_STATE -> {
                        g2.setColor(color);
                        g2.fillOval(x, y, 18, 18);
                    }
                    case END_STATE -> {
                        g2.setColor(color);
                        g2.fillOval(x, y, 18, 18);
                        g2.setColor(Color.WHITE);
                        g2.fillOval(x + 4, y + 4, 10, 10);
                        g2.setColor(color);
                        g2.fillOval(x + 6, y + 6, 6, 6);
                    }
                    case TASK_NODE, STATE, ACTION_NODE -> {
                        g2.setColor(color);
                        g2.fillRoundRect(x, y + 3, 22, 12, 4, 4);
                    }
                    case DECISION -> {
                        g2.setColor(color);
                        Polygon d = new Polygon(
                            new int[]{x + 9, x + 18, x + 9, x},
                            new int[]{y, y + 9, y + 18, y + 9}, 4
                        );
                        g2.fillPolygon(d);
                    }
                    case FORK, JOIN -> {
                        g2.setColor(color);
                        g2.fillRect(x, y + 6, 22, 6);
                    }
                }
                g2.dispose();
            }

            @Override public int getIconWidth() { return 24; }
            @Override public int getIconHeight() { return 20; }
        };
    }

    private Color getNodeColor(NodeType type) {
        return switch (type) {
            case START_STATE -> NodeRenderer.C_START;
            case END_STATE   -> NodeRenderer.C_END;
            case TASK_NODE   -> NodeRenderer.C_TASK;
            case STATE       -> NodeRenderer.C_STATE;
            case DECISION    -> NodeRenderer.C_DECISION;
            case FORK, JOIN  -> NodeRenderer.C_FORK_JOIN;
            case ACTION_NODE -> NodeRenderer.C_ACTION;
        };
    }
}
