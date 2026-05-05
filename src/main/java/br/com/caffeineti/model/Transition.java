package br.com.caffeineti.model;

public class Transition {
    private String id;
    private String name;
    private FlowNode source;
    private FlowNode target;
    private String condition;       // EL condition for decisions
    private String actionClass;     // action handler on transition
    private String actionExpression;

    public Transition(String id, String name, FlowNode source, FlowNode target) {
        this.id = id;
        this.name = name;
        this.source = source;
        this.target = target;
        source.getOutgoing().add(this);
        target.getIncoming().add(this);
    }

    public void detach() {
        source.getOutgoing().remove(this);
        target.getIncoming().remove(this);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public FlowNode getSource() { return source; }
    public FlowNode getTarget() { return target; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public String getActionClass() { return actionClass; }
    public void setActionClass(String actionClass) { this.actionClass = actionClass; }
    public String getActionExpression() { return actionExpression; }
    public void setActionExpression(String actionExpression) { this.actionExpression = actionExpression; }
}
