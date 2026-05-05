package br.com.caffeineti.model;

public class DecisionNode extends FlowNode {
    public DecisionNode(String id, String name, int x, int y) {
        super(id, name, x, y);
    }

    @Override
    public NodeType getType() { return NodeType.DECISION; }

    public String getExpression() { return getProperty("expression"); }
    public void setExpression(String expr) { setProperty("expression", expr); }

    public String getDecisionClass() { return getProperty("decision-class"); }
    public void setDecisionClass(String cls) { setProperty("decision-class", cls); }
}
