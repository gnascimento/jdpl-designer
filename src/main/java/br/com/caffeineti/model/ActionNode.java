package br.com.caffeineti.model;

public class ActionNode extends FlowNode {
    public ActionNode(String id, String name, int x, int y) {
        super(id, name, x, y);
    }

    @Override
    public NodeType getType() { return NodeType.ACTION_NODE; }

    public String getActionClass() { return getProperty("class"); }
    public void setActionClass(String cls) { setProperty("class", cls); }

    public String getActionExpression() { return getProperty("expression"); }
    public void setActionExpression(String expr) { setProperty("expression", expr); }
}
