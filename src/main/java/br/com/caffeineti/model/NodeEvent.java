package br.com.caffeineti.model;

import br.com.caffeineti.i18n.Messages;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a jPDL {@code <event>} element attached to a node.
 * Each event has a type (e.g. "node-enter") and a list of action entries.
 */
public class NodeEvent {

    // Common jPDL event types
    public static final String NODE_ENTER   = "node-enter";
    public static final String NODE_LEAVE   = "node-leave";
    public static final String TASK_CREATE  = "task-create";
    public static final String TASK_ASSIGN  = "task-assign";
    public static final String TASK_START   = "task-start";
    public static final String TASK_END     = "task-end";
    public static final String BEFORE_SIGNAL = "before-signal";
    public static final String AFTER_SIGNAL  = "after-signal";

    public static final String[] ALL_TYPES = {
        NODE_ENTER, NODE_LEAVE, TASK_CREATE, TASK_ASSIGN,
        TASK_START, TASK_END, BEFORE_SIGNAL, AFTER_SIGNAL
    };
    public static final String[] TASK_ONLY_TYPES = {
        NODE_ENTER, NODE_LEAVE, TASK_CREATE, TASK_ASSIGN, TASK_START, TASK_END
    };

    // -------------------------------------------------------------------------

    public static class ActionEntry {
        private String expression;
        private String handlerClass;

        public ActionEntry() {}
        public ActionEntry(String expression, String handlerClass) {
            this.expression = expression;
            this.handlerClass = handlerClass;
        }

        public String getExpression()   { return expression; }
        public void setExpression(String e) { this.expression = e; }
        public String getHandlerClass() { return handlerClass; }
        public void setHandlerClass(String c) { this.handlerClass = c; }

        @Override
        public String toString() {
            if (expression != null && !expression.isBlank()) return expression;
            if (handlerClass != null && !handlerClass.isBlank()) return "class: " + handlerClass;
            return Messages.get("events.empty");
        }
    }

    // -------------------------------------------------------------------------

    private String eventType;
    private final List<ActionEntry> actions = new ArrayList<>();

    public NodeEvent(String eventType) { this.eventType = eventType; }

    public String getEventType() { return eventType; }
    public void setEventType(String t) { this.eventType = t; }
    public List<ActionEntry> getActions() { return actions; }

    public void addAction(String expression, String handlerClass) {
        actions.add(new ActionEntry(expression, handlerClass));
    }

    @Override
    public String toString() { return eventType + " (" + Messages.get("events.actionCount", actions.size()) + ")"; }
}
