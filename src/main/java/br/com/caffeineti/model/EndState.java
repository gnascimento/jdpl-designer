package br.com.caffeineti.model;

public class EndState extends FlowNode {
    public EndState(String id, String name, int x, int y) {
        super(id, name, x, y);
    }

    @Override
    public NodeType getType() { return NodeType.END_STATE; }
}
