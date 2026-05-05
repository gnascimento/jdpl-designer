package br.com.caffeineti.model;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class FlowNode {
    private String id;
    private String name;
    private int x, y;
    private int width, height;
    private final List<Transition> outgoing = new ArrayList<>();
    private final List<Transition> incoming = new ArrayList<>();
    private final Map<String, String> properties = new LinkedHashMap<>();
    private final List<NodeEvent> events = new ArrayList<>();

    public FlowNode(String id, String name, int x, int y) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.width = getType().getDefaultWidth();
        this.height = getType().getDefaultHeight();
    }

    public abstract NodeType getType();

    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    public boolean contains(int px, int py) {
        return getBounds().contains(px, py);
    }

    public Point getCenter() {
        return new Point(x + width / 2, y + height / 2);
    }

    /** Returns the boundary point of this node in the direction of targetCenter. */
    public Point getConnectionPointToward(Point targetCenter) {
        Point center = getCenter();
        double dx = targetCenter.x - center.x;
        double dy = targetCenter.y - center.y;
        if (dx == 0 && dy == 0) return center;

        double halfW = width / 2.0;
        double halfH = height / 2.0;
        double tx, ty;

        if (Math.abs(dx) * halfH > Math.abs(dy) * halfW) {
            tx = (dx > 0) ? halfW : -halfW;
            ty = (dx == 0) ? 0 : tx * dy / dx;
        } else {
            ty = (dy > 0) ? halfH : -halfH;
            tx = (dy == 0) ? 0 : ty * dx / dy;
        }
        return new Point((int) (center.x + tx), (int) (center.y + ty));
    }

    /** Returns the 4 port points (top, right, bottom, left). */
    public Point[] getPorts() {
        return new Point[]{
            new Point(x + width / 2, y),
            new Point(x + width, y + height / 2),
            new Point(x + width / 2, y + height),
            new Point(x, y + height / 2)
        };
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return y; }
    public void setY(int y) { this.y = y; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public List<Transition> getOutgoing() { return outgoing; }
    public List<Transition> getIncoming() { return incoming; }
    public Map<String, String> getProperties() { return properties; }
    public List<NodeEvent> getEvents() { return events; }

    /** Returns the event with the given type, or null if not present. */
    public NodeEvent getEvent(String type) {
        return events.stream().filter(e -> type.equals(e.getEventType())).findFirst().orElse(null);
    }

    /** Gets or creates an event of the given type. */
    public NodeEvent getOrCreateEvent(String type) {
        NodeEvent ev = getEvent(type);
        if (ev == null) { ev = new NodeEvent(type); events.add(ev); }
        return ev;
    }
    public String getProperty(String key) { return properties.get(key); }
    public void setProperty(String key, String value) {
        if (value == null || value.isBlank()) properties.remove(key);
        else properties.put(key, value);
    }
}
