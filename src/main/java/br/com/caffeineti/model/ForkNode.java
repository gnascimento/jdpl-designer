package br.com.caffeineti.model;

public class ForkNode extends FlowNode {
    public ForkNode(String id, String name, int x, int y) {
        super(id, name, x, y);
    }

    @Override
    public NodeType getType() { return NodeType.FORK; }
}
