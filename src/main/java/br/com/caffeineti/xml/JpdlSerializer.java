package br.com.caffeineti.xml;

import br.com.caffeineti.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Serializes a ProcessDefinition to jPDL 3.2 XML. */
public class JpdlSerializer {

    public String serialize(ProcessDefinition process) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<process-definition xmlns=\"urn:jbpm.org:jpdl-3.2\"");
        sb.append(" name=\"").append(esc(process.getName())).append("\">\n\n");

        for (FlowNode node : process.getNodes()) {
            serializeNode(sb, node, process.getTransitions());
        }

        sb.append("</process-definition>\n");
        return sb.toString();
    }

    public void serialize(ProcessDefinition process, File file) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(serialize(process));
        }
    }

    private void serializeNode(StringBuilder sb, FlowNode node, List<Transition> allTransitions) {
        NodeType type = node.getType();
        String tag = type.getJpdlTag();

        switch (type) {
            case START_STATE -> {
                sb.append("  <start-state name=\"").append(esc(node.getName())).append("\">\n");
                appendTransitions(sb, node, "    ");
                sb.append("  </start-state>\n\n");
            }
            case END_STATE -> {
                sb.append("  <end-state name=\"").append(esc(node.getName())).append("\"/>\n\n");
            }
            case TASK_NODE -> {
                TaskNode tn = (TaskNode) node;
                sb.append("  <task-node name=\"").append(esc(node.getName())).append("\">\n");
                // task element
                sb.append("    <task name=\"").append(esc(node.getName())).append("\"");
                if (tn.getSwimlane() != null)
                    sb.append(" swimlane=\"").append(esc(tn.getSwimlane())).append("\"");
                if (tn.getActorId() != null)
                    sb.append(" actor-id=\"").append(esc(tn.getActorId())).append("\"");

                boolean hasDesc = tn.getDescription() != null;
                boolean hasAssignClass = tn.getAssignmentClass() != null;
                boolean hasAssignExpr = tn.getAssignmentExpression() != null;

                if (!hasDesc && !hasAssignClass && !hasAssignExpr) {
                    sb.append("/>\n");
                } else {
                    sb.append(">\n");
                    if (hasDesc)
                        sb.append("      <description>").append(esc(tn.getDescription())).append("</description>\n");
                    if (hasAssignClass || hasAssignExpr) {
                        sb.append("      <assignment");
                        if (hasAssignClass)
                            sb.append(" class=\"").append(esc(tn.getAssignmentClass())).append("\"");
                        if (hasAssignExpr)
                            sb.append(" expression=\"").append(esc(tn.getAssignmentExpression())).append("\"");
                        sb.append("/>\n");
                    }
                    sb.append("    </task>\n");
                }
                appendEvents(sb, node, "    ");
                appendTransitions(sb, node, "    ");
                sb.append("  </task-node>\n\n");
            }
            case STATE -> {
                sb.append("  <state name=\"").append(esc(node.getName())).append("\">\n");
                appendTransitions(sb, node, "    ");
                sb.append("  </state>\n\n");
            }
            case DECISION -> {
                DecisionNode dn = (DecisionNode) node;
                sb.append("  <decision name=\"").append(esc(node.getName())).append("\"");
                if (dn.getExpression() != null)
                    sb.append(" expression=\"").append(esc(dn.getExpression())).append("\"");
                if (dn.getDecisionClass() != null)
                    sb.append(" class=\"").append(esc(dn.getDecisionClass())).append("\"");
                sb.append(">\n");
                appendTransitions(sb, node, "    ");
                sb.append("  </decision>\n\n");
            }
            case FORK -> {
                sb.append("  <fork name=\"").append(esc(node.getName())).append("\">\n");
                appendTransitions(sb, node, "    ");
                sb.append("  </fork>\n\n");
            }
            case JOIN -> {
                sb.append("  <join name=\"").append(esc(node.getName())).append("\">\n");
                appendTransitions(sb, node, "    ");
                sb.append("  </join>\n\n");
            }
            case ACTION_NODE -> {
                ActionNode an = (ActionNode) node;
                sb.append("  <node name=\"").append(esc(node.getName())).append("\">\n");
                if (an.getActionClass() != null || an.getActionExpression() != null) {
                    sb.append("    <action");
                    if (an.getActionClass() != null)
                        sb.append(" class=\"").append(esc(an.getActionClass())).append("\"");
                    if (an.getActionExpression() != null)
                        sb.append(" expression=\"").append(esc(an.getActionExpression())).append("\"");
                    sb.append("/>\n");
                }
                appendEvents(sb, node, "    ");
                appendTransitions(sb, node, "    ");
                sb.append("  </node>\n\n");
            }
        }
    }

    private void appendEvents(StringBuilder sb, FlowNode node, String indent) {
        for (NodeEvent ev : node.getEvents()) {
            if (ev.getActions().isEmpty()) continue;
            sb.append(indent).append("<event type=\"").append(esc(ev.getEventType())).append("\">\n");
            for (NodeEvent.ActionEntry ae : ev.getActions()) {
                sb.append(indent).append("  <action");
                if (ae.getHandlerClass() != null && !ae.getHandlerClass().isBlank())
                    sb.append(" class=\"").append(esc(ae.getHandlerClass())).append("\"");
                if (ae.getExpression() != null && !ae.getExpression().isBlank())
                    sb.append(" expression=\"").append(esc(ae.getExpression())).append("\"");
                sb.append("/>\n");
            }
            sb.append(indent).append("</event>\n");
        }
    }

    private void appendTransitions(StringBuilder sb, FlowNode node, String indent) {
        for (Transition t : node.getOutgoing()) {
            sb.append(indent).append("<transition");
            if (t.getName() != null && !t.getName().isBlank())
                sb.append(" name=\"").append(esc(t.getName())).append("\"");
            sb.append(" to=\"").append(esc(t.getTarget().getName())).append("\"");

            boolean hasCondition = t.getCondition() != null && !t.getCondition().isBlank();
            boolean hasAction = t.getActionClass() != null || t.getActionExpression() != null;

            if (!hasCondition && !hasAction) {
                sb.append("/>\n");
            } else {
                sb.append(">\n");
                if (hasCondition)
                    sb.append(indent).append("  <condition expression=\"").append(esc(t.getCondition())).append("\"/>\n");
                if (hasAction) {
                    sb.append(indent).append("  <action");
                    if (t.getActionClass() != null)
                        sb.append(" class=\"").append(esc(t.getActionClass())).append("\"");
                    if (t.getActionExpression() != null)
                        sb.append(" expression=\"").append(esc(t.getActionExpression())).append("\"");
                    sb.append("/>\n");
                }
                sb.append(indent).append("</transition>\n");
            }
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
