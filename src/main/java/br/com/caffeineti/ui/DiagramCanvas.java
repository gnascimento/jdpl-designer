package br.com.caffeineti.ui;

import br.com.caffeineti.model.*;
import br.com.caffeineti.i18n.Messages;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * Interactive canvas for drawing jPDL process diagrams.
 *
 * Interactions:
 *  - Click on empty area (with palette selection active): places a new node
 *  - Click node body: selects it
 *  - Drag node body: moves it
 *  - Hover near node edge: shows port indicators (green dots)
 *  - Drag from port: starts transition creation; release on another node completes it
 *  - Click transition line: selects it
 *  - Delete / Backspace: removes selected element
 *  - Ctrl+Scroll: zoom; Middle-drag: pan
 */
public class DiagramCanvas extends JPanel {

    public interface SelectionListener {
        void onNodeSelected(FlowNode node);
        void onTransitionSelected(Transition t);
        void onSelectionCleared();
        void onProcessChanged();
    }

    // ---- State ----
    private ProcessDefinition process;
    private NodeType pendingNodeType = null;  // set when palette item clicked

    private FlowNode      selectedNode       = null;
    private Transition    selectedTransition = null;
    private FlowNode      hoveredNode        = null;

    // Drag-move state
    private FlowNode  draggingNode  = null;
    private int       dragOffsetX, dragOffsetY;
    /** Target swimlane highlighted while dragging a TaskNode; null if not applicable. */
    private String    dragTargetSwimlane = null;

    // Transition-draw state
    private FlowNode connectSource = null;
    private Point    connectEnd    = null;      // current mouse pos (canvas coords)
    private int      connectPortIndex = -1;     // source port

    // Pan state
    private Point    panStart      = null;
    private int      panX = 0, panY = 0;

    // Zoom
    private double scale = 1.0;

    private final List<SelectionListener> listeners = new ArrayList<>();

    private static final int PORT_HIT_RADIUS   = 10;
    private static final int TRANSITION_HIT_PX = 8;   // pixels from segment to register a click
    private static final Color BG_COLOR   = new Color(0xF5F5F5);
    private static final Color GRID_COLOR = new Color(0xDDDDDD);
    private static final int   GRID_SIZE  = 20;

    // Swimlane header width (in canvas coordinates)
    private static final int SWIMLANE_HDR = 52;

    // Colours for up to 8 swimlane bands
    private static final Color[] BAND_BG  = {
        new Color(0xE3F2FD), new Color(0xE8F5E9), new Color(0xFFF3E0),
        new Color(0xF3E5F5), new Color(0xE0F7FA), new Color(0xFCE4EC),
        new Color(0xFFF9C4), new Color(0xEFEBE9)
    };
    private static final Color[] BAND_HDR = {
        new Color(0xBBDEFB), new Color(0xC8E6C9), new Color(0xFFE0B2),
        new Color(0xE1BEE7), new Color(0xB2EBF2), new Color(0xF8BBD0),
        new Color(0xFFF59D), new Color(0xD7CCC8)
    };
    private static final Color DRAG_HIGHLIGHT = new Color(0x1565C0);

    public DiagramCanvas(ProcessDefinition process) {
        this.process = process;
        setBackground(BG_COLOR);
        setPreferredSize(new Dimension(2000, 1500));
        setFocusable(true);
        setupMouseHandlers();
        setupKeyHandlers();
    }

    public void setProcess(ProcessDefinition process) {
        this.process = process;
        selectedNode = null;
        selectedTransition = null;
        hoveredNode = null;
        draggingNode = null;
        connectSource = null;
        repaint();
    }

    public void addSelectionListener(SelectionListener l) { listeners.add(l); }

    /** Called by the palette panel to set the node type to be placed next. */
    public void setPendingNodeType(NodeType type) {
        pendingNodeType = type;
        setCursor(type == null ? Cursor.getDefaultCursor()
                               : Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    }

    public NodeType getPendingNodeType() { return pendingNodeType; }

    // =========================================================================
    // Painting
    // =========================================================================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,    RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Apply pan + zoom transform
        g2.translate(panX, panY);
        g2.scale(scale, scale);

        // Swimlane bands (drawn below everything else)
        paintSwimlanes(g2);

        // Grid
        paintGrid(g2);

        // Transitions
        for (Transition t : process.getTransitions()) {
            NodeRenderer.drawTransition(g2, t, t == selectedTransition);
        }

        // Rubber-band transition
        if (connectSource != null && connectEnd != null) {
            Point portPt = connectSource.getPorts()[connectPortIndex];
            NodeRenderer.drawPendingTransition(g2, portPt, connectEnd);
        }

        // Nodes
        for (FlowNode node : process.getNodes()) {
            boolean showPorts = (node == selectedNode || node == hoveredNode);
            NodeRenderer.draw(g2, node, node == selectedNode, showPorts);
        }

        // Hover cursor hint
        if (pendingNodeType != null) {
            g2.setFont(new Font("SansSerif", Font.ITALIC, 11));
            g2.setColor(new Color(0x90A4AE));
            g2.drawString(Messages.get("canvas.placeHint", pendingNodeType.getDisplayName()), 10, 20);
        }

        g2.dispose();
    }

    // =========================================================================
    // Swimlane bands
    // =========================================================================

    private void paintSwimlanes(Graphics2D g) {
        List<String> allLanes = process.getAllSwimlaneNames();
        if (allLanes.isEmpty()) return;

        // Build map: lane name → task nodes
        Map<String, List<FlowNode>> laneNodes = new LinkedHashMap<>();
        for (String lane : allLanes) laneNodes.put(lane, new ArrayList<>());
        for (FlowNode n : process.getNodes()) {
            if (n instanceof TaskNode tn && tn.getSwimlane() != null && !tn.getSwimlane().isBlank()) {
                laneNodes.computeIfAbsent(tn.getSwimlane(), k -> new ArrayList<>()).add(tn);
            }
        }

        final int PAD        = 22;
        final int EMPTY_H    = 80;   // height for swimlanes with no nodes yet
        final int BIG        = 8000;
        final int MIN_HDR_H  = 40;

        // Compute bottom of last non-empty band (for positioning empty ones below)
        int bottomY = 60;
        for (FlowNode n : process.getNodes()) bottomY = Math.max(bottomY, n.getY() + n.getHeight() + PAD + 20);

        int colorIdx = 0;
        int emptyNextY = bottomY;

        for (Map.Entry<String, List<FlowNode>> entry : laneNodes.entrySet()) {
            String lane = entry.getKey();
            List<FlowNode> nodes = entry.getValue();

            int minY, maxY;
            if (nodes.isEmpty()) {
                minY = emptyNextY;
                maxY = emptyNextY + EMPTY_H;
                emptyNextY = maxY + 4;
            } else {
                minY = nodes.stream().mapToInt(FlowNode::getY).min().orElse(0) - PAD;
                maxY = nodes.stream().mapToInt(n -> n.getY() + n.getHeight()).max().orElse(100) + PAD;
            }
            int bandH = Math.max(MIN_HDR_H, maxY - minY);

            // Band background
            g.setColor(BAND_BG[colorIdx % BAND_BG.length]);
            g.fillRect(SWIMLANE_HDR, minY, BIG, bandH);

            // Header
            g.setColor(BAND_HDR[colorIdx % BAND_HDR.length]);
            g.fillRect(0, minY, SWIMLANE_HDR, bandH);

            // Borders — thicker + highlighted when this is the drag-drop target
            boolean isDropTarget = lane.equals(dragTargetSwimlane);
            if (isDropTarget) {
                g.setColor(DRAG_HIGHLIGHT);
                g.setStroke(new BasicStroke(2.5f));
            } else {
                g.setColor(new Color(0, 0, 0, 35));
                g.setStroke(new BasicStroke(0.8f));
            }
            g.drawRect(0, minY, BIG + SWIMLANE_HDR, bandH);
            g.drawLine(SWIMLANE_HDR, minY, SWIMLANE_HDR, minY + bandH);

            // Label rotated in header
            g.setColor(new Color(0x37474F));
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            FontMetrics fm = g.getFontMetrics();
            AffineTransform savedTx = g.getTransform();
            g.translate(SWIMLANE_HDR / 2, minY + bandH / 2);
            g.rotate(-Math.PI / 2);
            int tw = fm.stringWidth(lane);
            g.drawString(lane, -tw / 2, fm.getAscent() / 2 - 1);
            if (nodes.isEmpty()) {
                g.setFont(new Font("SansSerif", Font.ITALIC, 9));
                g.setColor(new Color(0x90A4AE));
                String hint = Messages.get("canvas.emptySwimlane");
                int hw = g.getFontMetrics().stringWidth(hint);
                g.drawString(hint, -hw / 2, fm.getAscent() / 2 + 12);
            }
            g.setTransform(savedTx);

            colorIdx++;
        }
    }

    /**
     * Returns the name of the swimlane band whose Y range contains {@code canvasY},
     * excluding {@code excludeNode} from its lane's node list (used while dragging).
     * Returns {@code null} if the point is not inside any defined band.
     */
    private String findSwimlaneAt(int canvasY, FlowNode excludeNode) {
        List<String> allLanes = process.getAllSwimlaneNames();
        if (allLanes.isEmpty()) return null;

        Map<String, List<FlowNode>> laneNodes = new LinkedHashMap<>();
        for (String lane : allLanes) laneNodes.put(lane, new ArrayList<>());
        for (FlowNode n : process.getNodes()) {
            if (n == excludeNode) continue;
            if (n instanceof TaskNode tn && tn.getSwimlane() != null && !tn.getSwimlane().isBlank()) {
                laneNodes.computeIfAbsent(tn.getSwimlane(), k -> new ArrayList<>()).add(tn);
            }
        }

        final int PAD     = 22;
        final int EMPTY_H = 80;
        final int MIN_HDR_H = 40;

        int bottomY = 60;
        for (FlowNode n : process.getNodes()) {
            if (n == excludeNode) continue;
            bottomY = Math.max(bottomY, n.getY() + n.getHeight() + PAD + 20);
        }

        int emptyNextY = bottomY;
        for (Map.Entry<String, List<FlowNode>> entry : laneNodes.entrySet()) {
            List<FlowNode> nodes = entry.getValue();
            int minY, maxY;
            if (nodes.isEmpty()) {
                minY = emptyNextY;
                maxY = emptyNextY + EMPTY_H;
                emptyNextY = maxY + 4;
            } else {
                minY = nodes.stream().mapToInt(FlowNode::getY).min().orElse(0) - PAD;
                maxY = nodes.stream().mapToInt(n -> n.getY() + n.getHeight()).max().orElse(100) + PAD;
            }
            int bandH = Math.max(MIN_HDR_H, maxY - minY);
            if (canvasY >= minY && canvasY <= minY + bandH) return entry.getKey();
        }
        return null;
    }

    private void paintGrid(Graphics2D g) {
        g.setColor(GRID_COLOR);
        g.setStroke(new BasicStroke(0.5f));
        Rectangle clip = g.getClipBounds();
        if (clip == null) clip = new Rectangle(0, 0, getWidth(), getHeight());

        int startX = (clip.x / GRID_SIZE) * GRID_SIZE;
        int startY = (clip.y / GRID_SIZE) * GRID_SIZE;
        for (int x = startX; x < clip.x + clip.width + GRID_SIZE; x += GRID_SIZE)
            g.drawLine(x, clip.y, x, clip.y + clip.height);
        for (int y = startY; y < clip.y + clip.height + GRID_SIZE; y += GRID_SIZE)
            g.drawLine(clip.x, y, clip.x + clip.width, y);
    }

    // =========================================================================
    // Mouse Handlers
    // =========================================================================

    private void setupMouseHandlers() {
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                Point cp = toCanvas(e.getPoint());

                if (SwingUtilities.isMiddleMouseButton(e)) {
                    panStart = e.getPoint();
                    return;
                }

                if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(e, cp);
                    return;
                }

                // Place new node
                if (pendingNodeType != null) {
                    NodeType type = pendingNodeType;
                    int nx = snap(cp.x - type.getDefaultWidth() / 2);
                    int ny = snap(cp.y - type.getDefaultHeight() / 2);
                    FlowNode node = process.createNode(type, nx, ny);
                    setPendingNodeType(null);
                    selectNode(node);
                    fireProcessChanged();
                    repaint();
                    return;
                }

                // Check if clicking on a port (transition creation)
                FlowNode portNode = findNodeAt(cp);
                if (portNode != null) {
                    int portIdx = findPortAt(portNode, cp);
                    if (portIdx >= 0) {
                        connectSource = portNode;
                        connectPortIndex = portIdx;
                        connectEnd = cp;
                        selectNode(null);
                        repaint();
                        return;
                    }
                }

                // Check if clicking on a node (selection / drag)
                FlowNode node = findNodeAt(cp);
                if (node != null) {
                    selectNode(node);
                    draggingNode = node;
                    dragOffsetX = cp.x - node.getX();
                    dragOffsetY = cp.y - node.getY();
                    repaint();
                    return;
                }

                // Check if clicking on a transition
                Transition t = findTransitionAt(cp);
                if (t != null) {
                    selectTransition(t);
                    repaint();
                    return;
                }

                // Click+drag on empty area: pan the canvas.
                // A plain click without drag simply deselects (handled in mouseReleased).
                clearSelection();
                panStart = e.getPoint();
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point cp = toCanvas(e.getPoint());

                if (panStart != null) {
                    int dx = e.getX() - panStart.x;
                    int dy = e.getY() - panStart.y;
                    panX += dx;
                    panY += dy;
                    panStart = e.getPoint();
                    updateScrollableSize();
                    repaint();
                    return;
                }

                if (connectSource != null) {
                    connectEnd = cp;
                    repaint();
                    return;
                }

                if (draggingNode != null) {
                    draggingNode.setX(snap(cp.x - dragOffsetX));
                    draggingNode.setY(snap(cp.y - dragOffsetY));
                    if (draggingNode instanceof TaskNode) {
                        int centerY = draggingNode.getY() + draggingNode.getHeight() / 2;
                        dragTargetSwimlane = findSwimlaneAt(centerY, draggingNode);
                    }
                    updateScrollableSize();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (panStart != null) {
                    panStart = null;
                    // Restore cursor based on what's under the pointer now
                    Point cp = toCanvas(e.getPoint());
                    FlowNode n = findNodeAt(cp);
                    if (n != null && findPortAt(n, cp) >= 0)
                        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    else if (pendingNodeType != null)
                        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    else
                        setCursor(Cursor.getDefaultCursor());
                    return;
                }

                Point cp = toCanvas(e.getPoint());

                if (connectSource != null) {
                    FlowNode target = findNodeAt(cp);
                    if (target != null && target != connectSource) {
                        Transition t = process.createTransition(connectSource, target);
                        selectTransition(t);
                        fireProcessChanged();
                    }
                    connectSource = null;
                    connectEnd = null;
                    connectPortIndex = -1;
                    repaint();
                    return;
                }

                if (draggingNode != null) {
                    // Apply swimlane change when a TaskNode was dragged into a different band
                    if (draggingNode instanceof TaskNode draggedTn && dragTargetSwimlane != null
                            && !Objects.equals(dragTargetSwimlane, draggedTn.getSwimlane())) {
                        draggedTn.setSwimlane(dragTargetSwimlane);
                    }
                    dragTargetSwimlane = null;
                    draggingNode = null;
                    fireProcessChanged();
                    if (selectedNode != null) refreshSelectedNode();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                Point cp = toCanvas(e.getPoint());
                FlowNode prev = hoveredNode;
                hoveredNode = findNodeAt(cp);

                // Change cursor when near a port
                if (hoveredNode != null && findPortAt(hoveredNode, cp) >= 0) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                } else if (pendingNodeType == null) {
                    setCursor(Cursor.getDefaultCursor());
                }

                if (prev != hoveredNode) repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                // Zoom on any scroll (trackpad two-finger swipe or mouse wheel),
                // centered on the current cursor position.
                double rotation = e.getPreciseWheelRotation(); // negative = zoom in
                double factor = Math.pow(0.88, rotation);      // smooth exponential
                double oldScale = scale;
                scale = Math.min(4.0, Math.max(0.15, scale * factor));
                double ratio = scale / oldScale;
                // Keep the canvas point under the cursor fixed
                panX = (int) Math.round(e.getX() - (e.getX() - panX) * ratio);
                panY = (int) Math.round(e.getY() - (e.getY() - panY) * ratio);
                updateScrollableSize();
                repaint();
            }
        };

        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
    }

    private void setupKeyHandlers() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    if (selectedNode != null) {
                        process.removeNode(selectedNode);
                        clearSelection();
                        fireProcessChanged();
                        repaint();
                    } else if (selectedTransition != null) {
                        process.removeTransition(selectedTransition);
                        clearSelection();
                        fireProcessChanged();
                        repaint();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    if (pendingNodeType != null) {
                        setPendingNodeType(null);
                        repaint();
                    }
                    if (connectSource != null) {
                        connectSource = null; connectEnd = null;
                        repaint();
                    }
                }
            }
        });
    }

    // =========================================================================
    // Context Menu
    // =========================================================================

    private void showContextMenu(MouseEvent e, Point cp) {
        FlowNode node = findNodeAt(cp);
        JPopupMenu menu = new JPopupMenu();

        if (node != null) {
            selectNode(node);
            repaint();
            JMenuItem del = new JMenuItem(Messages.get("context.deleteNode"));
            del.addActionListener(ev -> {
                process.removeNode(node);
                clearSelection();
                fireProcessChanged();
                repaint();
            });
            menu.add(del);
        } else {
            Transition t = findTransitionAt(cp);
            if (t != null) {
                selectTransition(t);
                repaint();
                JMenuItem del = new JMenuItem(Messages.get("context.deleteTransition"));
                del.addActionListener(ev -> {
                    process.removeTransition(t);
                    clearSelection();
                    fireProcessChanged();
                    repaint();
                });
                menu.add(del);
            } else {
                // Add new node submenu
                JMenu addMenu = new JMenu(Messages.get("context.addNode"));
                for (NodeType nt : NodeType.values()) {
                    JMenuItem mi = new JMenuItem(nt.getDisplayName());
                    int px = snap(cp.x - nt.getDefaultWidth() / 2);
                    int py = snap(cp.y - nt.getDefaultHeight() / 2);
                    mi.addActionListener(ev -> {
                        FlowNode n = process.createNode(nt, px, py);
                        selectNode(n);
                        fireProcessChanged();
                        repaint();
                    });
                    addMenu.add(mi);
                }
                menu.add(addMenu);
            }
        }

        menu.show(this, e.getX(), e.getY());
    }

    // =========================================================================
    // Hit Testing
    // =========================================================================

    private FlowNode findNodeAt(Point cp) {
        // Iterate in reverse so top-most nodes are hit first
        List<FlowNode> nodes = process.getNodes();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            if (nodes.get(i).contains(cp.x, cp.y)) return nodes.get(i);
        }
        return null;
    }

    private int findPortAt(FlowNode node, Point cp) {
        Point[] ports = node.getPorts();
        for (int i = 0; i < ports.length; i++) {
            double d = Math.hypot(cp.x - ports[i].x, cp.y - ports[i].y);
            if (d <= PORT_HIT_RADIUS) return i;
        }
        return -1;
    }

    private Transition findTransitionAt(Point cp) {
        for (Transition t : process.getTransitions()) {
            if (isNearTransition(t, cp)) return t;
        }
        return null;
    }

    private boolean isNearTransition(Transition t, Point cp) {
        // Use the SAME routing that NodeRenderer uses for drawing, so click targets
        // are always exactly on the visible line segments.
        // Divide hit radius by scale so 8 screen pixels always work regardless of zoom.
        double hitRadius = TRANSITION_HIT_PX / scale;
        List<Point> pts = NodeRenderer.routeTransition(t);
        for (int i = 0; i < pts.size() - 1; i++) {
            if (distToSegment(cp, pts.get(i), pts.get(i + 1)) <= hitRadius) return true;
        }
        return false;
    }

    /** Perpendicular distance from point p to the finite segment [a, b]. */
    private static double distToSegment(Point p, Point a, Point b) {
        double dx = b.x - a.x, dy = b.y - a.y;
        if (dx == 0 && dy == 0) return Math.hypot(p.x - a.x, p.y - a.y);
        double t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy));
    }

    // =========================================================================
    // Selection helpers
    // =========================================================================

    private void selectNode(FlowNode node) {
        selectedNode = node;
        selectedTransition = null;
        if (node != null) listeners.forEach(l -> l.onNodeSelected(node));
        else              listeners.forEach(SelectionListener::onSelectionCleared);
    }

    private void selectTransition(Transition t) {
        selectedTransition = t;
        selectedNode = null;
        listeners.forEach(l -> l.onTransitionSelected(t));
    }

    private void clearSelection() {
        selectedNode = null;
        selectedTransition = null;
        listeners.forEach(SelectionListener::onSelectionCleared);
    }

    private void fireProcessChanged() {
        listeners.forEach(SelectionListener::onProcessChanged);
    }

    // =========================================================================
    // Coordinate utilities
    // =========================================================================

    /** Convert screen point to canvas (model) coordinates. */
    private Point toCanvas(Point screen) {
        return new Point((int) ((screen.x - panX) / scale), (int) ((screen.y - panY) / scale));
    }

    private int snap(int v) {
        return (v / GRID_SIZE) * GRID_SIZE;
    }

    // =========================================================================
    // Accessors used by MainFrame
    // =========================================================================

    /**
     * Updates the JPanel's preferred size to match the zoomed/panned content bounds
     * so the JScrollPane scrollbars always reflect the actual diagram extent.
     */
    private void updateScrollableSize() {
        int maxX = 300, maxY = 300;
        for (FlowNode n : process.getNodes()) {
            maxX = Math.max(maxX, n.getX() + n.getWidth()  + 200);
            maxY = Math.max(maxY, n.getY() + n.getHeight() + 200);
        }
        // Convert content extent to screen pixels, accounting for current pan and scale
        int pw = (int)(maxX * scale) + Math.max(0, panX) + 200;
        int ph = (int)(maxY * scale) + Math.max(0, panY) + 200;

        Container parent = getParent();
        int minW = (parent instanceof JViewport vp) ? vp.getWidth()  : Math.max(800, getWidth());
        int minH = (parent instanceof JViewport vp) ? vp.getHeight() : Math.max(600, getHeight());

        setPreferredSize(new Dimension(Math.max(minW, pw), Math.max(minH, ph)));
        revalidate();
    }

    public FlowNode getSelectedNode() { return selectedNode; }
    public Transition getSelectedTransition() { return selectedTransition; }

    public void refreshSelectedNode() {
        if (selectedNode != null) listeners.forEach(l -> l.onNodeSelected(selectedNode));
        repaint();
    }

    public void fitToWindow() {
        List<FlowNode> nodes = process.getNodes();
        if (nodes.isEmpty()) return;

        // Use viewport (visible) dimensions, not the full canvas preferred size.
        // The canvas lives inside a JScrollPane > JViewport.
        Container parent = getParent();
        int viewW = (parent instanceof JViewport vp) ? vp.getWidth() : getWidth();
        int viewH = (parent instanceof JViewport vp) ? vp.getHeight() : getHeight();

        if (viewW <= 0 || viewH <= 0) {
            // Layout not yet complete – defer one frame
            SwingUtilities.invokeLater(this::fitToWindow);
            return;
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (FlowNode n : nodes) {
            Rectangle b = n.getBounds();
            minX = Math.min(minX, b.x);          minY = Math.min(minY, b.y);
            maxX = Math.max(maxX, b.x + b.width); maxY = Math.max(maxY, b.y + b.height);
        }

        int diagramW = maxX - minX;
        int diagramH = maxY - minY;
        int margin = 60;
        double sx = (double)(viewW - margin * 2) / Math.max(1, diagramW);
        double sy = (double)(viewH - margin * 2) / Math.max(1, diagramH);
        scale = Math.min(1.5, Math.min(sx, sy));
        panX  = (int)(margin - minX * scale + (viewW - margin * 2 - diagramW * scale) / 2.0);
        panY  = (int)(margin - minY * scale + (viewH - margin * 2 - diagramH * scale) / 2.0);
        updateScrollableSize();
        repaint();
    }

    // =========================================================================
    // Auto Layout — hierarchical (Sugiyama-inspired layers)
    // =========================================================================

    public void autoLayout() {
        List<FlowNode> nodes = process.getNodes();
        if (nodes.isEmpty()) return;

        // --- 1. Find root nodes (no incoming transitions) ---
        List<FlowNode> roots = nodes.stream()
                .filter(n -> n.getIncoming().isEmpty())
                .toList();
        if (roots.isEmpty()) roots = List.of(nodes.get(0));

        // --- 2. Longest-path layer assignment (handles DAGs; cycles capped) ---
        Map<FlowNode, Integer> layerOf = new LinkedHashMap<>();
        for (FlowNode n : nodes) layerOf.put(n, -1);
        for (FlowNode r : roots) layerOf.put(r, 0);

        int maxIter = nodes.size() * nodes.size() + 1; // cap against infinite cycles
        boolean changed = true;
        while (changed && maxIter-- > 0) {
            changed = false;
            for (FlowNode n : nodes) {
                int cur = layerOf.get(n);
                if (cur < 0) continue;
                for (Transition t : n.getOutgoing()) {
                    FlowNode tgt = t.getTarget();
                    if (layerOf.get(tgt) < cur + 1) {
                        layerOf.put(tgt, cur + 1);
                        changed = true;
                    }
                }
            }
        }
        // Disconnected nodes → place after last layer
        int maxLayer = layerOf.values().stream().mapToInt(v -> Math.max(v, 0)).max().orElse(0);
        for (FlowNode n : nodes) if (layerOf.get(n) < 0) layerOf.put(n, maxLayer + 1);

        // --- 3. Group by layer, sort within each layer by swimlane so same-lane nodes
        //        end up adjacent and the swimlane bands form clean horizontal strips ---
        Map<Integer, List<FlowNode>> byLayer = new TreeMap<>();
        for (FlowNode n : nodes) byLayer.computeIfAbsent(layerOf.get(n), k -> new ArrayList<>()).add(n);

        for (List<FlowNode> col : byLayer.values()) {
            col.sort(Comparator.comparing(n -> {
                if (n instanceof TaskNode tn && tn.getSwimlane() != null) return tn.getSwimlane();
                return "\uFFFF"; // non-swimlane nodes go last in column
            }));
        }

        // --- 4. Assign pixel positions ---
        final int H_GAP    = 80;   // gap between columns
        final int V_GAP    = 36;   // gap between rows in same column
        // Leave room for the swimlane header on the left
        final int START_X  = SWIMLANE_HDR + 28;
        final int START_Y  = 80;

        // Precompute each layer's x offset based on the widest node in the preceding layer
        int[] layerX = new int[byLayer.size() + 1];
        layerX[0] = START_X;
        int layerIdx = 0;
        for (Map.Entry<Integer, List<FlowNode>> entry : byLayer.entrySet()) {
            int maxW = entry.getValue().stream().mapToInt(FlowNode::getWidth).max().orElse(140);
            if (layerIdx + 1 < layerX.length) layerX[layerIdx + 1] = layerX[layerIdx] + maxW + H_GAP;
            layerIdx++;
        }

        // Center each layer's nodes vertically around the tallest column
        int totalLayersH = byLayer.values().stream()
                .mapToInt(l -> l.stream().mapToInt(n -> n.getHeight() + V_GAP).sum()).max().orElse(0);

        layerIdx = 0;
        for (Map.Entry<Integer, List<FlowNode>> entry : byLayer.entrySet()) {
            List<FlowNode> col = entry.getValue();
            int colH = col.stream().mapToInt(n -> n.getHeight() + V_GAP).sum() - V_GAP;
            int x   = layerX[layerIdx];
            int y   = START_Y + (totalLayersH - colH) / 2;
            int maxW = col.stream().mapToInt(FlowNode::getWidth).max().orElse(140);
            for (FlowNode n : col) {
                // Center-align narrow nodes (fork/join) horizontally in their column
                n.setX(x + (maxW - n.getWidth()) / 2);
                n.setY(Math.max(START_Y, y));
                y += n.getHeight() + V_GAP;
            }
            layerIdx++;
        }

        fireProcessChanged();
        fitToWindow();      // also calls updateScrollableSize + repaint
    }
}
