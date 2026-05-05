package br.com.caffeineti.model;

import br.com.caffeineti.i18n.Messages;

public enum NodeType {
    START_STATE("Start State", "start-state", 80, 80),
    END_STATE("End State", "end-state", 80, 80),
    TASK_NODE("Task Node", "task-node", 140, 60),
    STATE("State", "state", 120, 60),
    DECISION("Decision", "decision", 100, 60),
    FORK("Fork", "fork", 120, 20),
    JOIN("Join", "join", 120, 20),
    ACTION_NODE("Action Node", "node", 140, 60);

    private final String displayName;
    private final String jpdlTag;
    private final int defaultWidth;
    private final int defaultHeight;

    NodeType(String displayName, String jpdlTag, int defaultWidth, int defaultHeight) {
        this.displayName = displayName;
        this.jpdlTag = jpdlTag;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
    }

    public String getDisplayName() { return Messages.get("nodeType." + name()); }
    public String getDefaultDisplayName() { return displayName; }
    public String getJpdlTag() { return jpdlTag; }
    public int getDefaultWidth() { return defaultWidth; }
    public int getDefaultHeight() { return defaultHeight; }
}
