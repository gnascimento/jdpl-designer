package br.com.caffeineti.model;

public class StartState extends FlowNode {
    public StartState(String id, String name, int x, int y) {
        super(id, name, x, y);
    }

    @Override
    public NodeType getType() { return NodeType.START_STATE; }
}
