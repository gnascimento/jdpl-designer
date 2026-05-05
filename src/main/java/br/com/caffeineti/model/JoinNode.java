package br.com.caffeineti.model;

public class JoinNode extends FlowNode {
    public JoinNode(String id, String name, int x, int y) {
        super(id, name, x, y);
    }

    @Override
    public NodeType getType() { return NodeType.JOIN; }
}
