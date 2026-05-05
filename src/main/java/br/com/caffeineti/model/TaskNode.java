package br.com.caffeineti.model;

public class TaskNode extends FlowNode {
    public TaskNode(String id, String name, int x, int y) {
        super(id, name, x, y);
    }

    @Override
    public NodeType getType() { return NodeType.TASK_NODE; }

    // Convenience accessors for jPDL task-node specific properties
    public String getSwimlane() { return getProperty("swimlane"); }
    public void setSwimlane(String swimlane) { setProperty("swimlane", swimlane); }

    public String getAssignmentClass() { return getProperty("assignment-class"); }
    public void setAssignmentClass(String cls) { setProperty("assignment-class", cls); }

    public String getAssignmentExpression() { return getProperty("assignment-expr"); }
    public void setAssignmentExpression(String expr) { setProperty("assignment-expr", expr); }

    public String getActorId() { return getProperty("actor-id"); }
    public void setActorId(String actorId) { setProperty("actor-id", actorId); }

    public String getDescription() { return getProperty("description"); }
    public void setDescription(String desc) { setProperty("description", desc); }
}
