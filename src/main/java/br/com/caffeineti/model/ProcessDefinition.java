package br.com.caffeineti.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ProcessDefinition {
    private String name = "MyProcess";
    private final List<FlowNode> nodes = new ArrayList<>();
    private final List<Transition> transitions = new ArrayList<>();
    /** Ordered list of swimlane names explicitly defined for this process. */
    private final List<String> swimlaneNames = new ArrayList<>();
    private final AtomicInteger nodeCounter = new AtomicInteger(1);
    private final AtomicInteger transCounter = new AtomicInteger(1);

    public FlowNode createNode(NodeType type, int x, int y) {
        String id = type.getJpdlTag() + "-" + nodeCounter.getAndIncrement();
        String name = type.getDisplayName() + " " + (nodeCounter.get() - 1);
        FlowNode node = switch (type) {
            case START_STATE -> new StartState(id, name, x, y);
            case END_STATE   -> new EndState(id, name, x, y);
            case TASK_NODE   -> new TaskNode(id, name, x, y);
            case STATE       -> new StateNode(id, name, x, y);
            case DECISION    -> new DecisionNode(id, name, x, y);
            case FORK        -> new ForkNode(id, name, x, y);
            case JOIN        -> new JoinNode(id, name, x, y);
            case ACTION_NODE -> new ActionNode(id, name, x, y);
        };
        nodes.add(node);
        return node;
    }

    public Transition createTransition(FlowNode source, FlowNode target) {
        String id = "t" + transCounter.getAndIncrement();
        Transition t = new Transition(id, "", source, target);
        transitions.add(t);
        return t;
    }

    public void addNode(FlowNode node) { nodes.add(node); }

    public void addTransition(Transition t) { transitions.add(t); }

    public void removeNode(FlowNode node) {
        List<Transition> toRemove = new ArrayList<>();
        for (Transition t : transitions) {
            if (t.getSource() == node || t.getTarget() == node) toRemove.add(t);
        }
        toRemove.forEach(this::removeTransition);
        nodes.remove(node);
    }

    public void removeTransition(Transition t) {
        t.detach();
        transitions.remove(t);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<FlowNode> getNodes() { return nodes; }
    public List<Transition> getTransitions() { return transitions; }
    public List<String> getSwimlaneNames() { return swimlaneNames; }

    /**
     * Returns all swimlane names: explicitly defined ones first, then any
     * additional ones found in task-node properties (so parsed files show all lanes).
     */
    public List<String> getAllSwimlaneNames() {
        List<String> all = new ArrayList<>(swimlaneNames);
        for (FlowNode n : nodes) {
            if (n instanceof TaskNode tn) {
                String lane = tn.getSwimlane();
                if (lane != null && !lane.isBlank() && !all.contains(lane)) all.add(lane);
            }
        }
        return all;
    }

    public void clear() {
        transitions.forEach(Transition::detach);
        nodes.clear();
        transitions.clear();
        swimlaneNames.clear();
        nodeCounter.set(1);
        transCounter.set(1);
    }
}
