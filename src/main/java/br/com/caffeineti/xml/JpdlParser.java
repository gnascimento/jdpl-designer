package br.com.caffeineti.xml;

import br.com.caffeineti.model.*;
import org.w3c.dom.*;

import javax.xml.parsers.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.xml.sax.InputSource;

/** Parses jPDL 3.2 XML into a ProcessDefinition. Positions are auto-laid-out. */
public class JpdlParser {

    public ProcessDefinition parse(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Force UTF-8 so special characters are always decoded correctly regardless
        // of the JVM's default charset or the OS locale.
        InputSource src = new InputSource(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        src.setEncoding("UTF-8");
        Document doc = factory.newDocumentBuilder().parse(src);
        return buildProcess(doc);
    }

    public ProcessDefinition parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        InputSource src = new InputSource(new StringReader(xml));
        src.setEncoding("UTF-8");
        Document doc = factory.newDocumentBuilder().parse(src);
        return buildProcess(doc);
    }

    private ProcessDefinition buildProcess(Document doc) {
        ProcessDefinition process = new ProcessDefinition();
        Element root = doc.getDocumentElement();
        String processName = root.getAttribute("name");
        if (!processName.isBlank()) process.setName(processName);

        Map<String, FlowNode> nodesByName = new HashMap<>();
        int col = 0, row = 0;
        int startX = 80, startY = 80, hGap = 200, vGap = 120;

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element el)) continue;
            String tag = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
            String name = el.getAttribute("name");

            int x = startX + col * hGap;
            int y = startY + row * vGap;

            FlowNode node = switch (tag) {
                case "start-state"  -> new StartState("s" + i, name, x, y);
                case "end-state"    -> new EndState("e" + i, name, x, y);
                case "task-node"    -> buildTaskNode("t" + i, name, x, y, el);
                case "state"        -> new StateNode("st" + i, name, x, y);
                case "decision"     -> buildDecision("d" + i, name, x, y, el);
                case "fork"         -> new ForkNode("f" + i, name, x, y);
                case "join"         -> new JoinNode("j" + i, name, x, y);
                case "node"         -> buildActionNode("n" + i, name, x, y, el);
                default             -> null;
            };

            if (node != null) {
                process.addNode(node);
                nodesByName.put(name, node);
                col++;
                if (col > 4) { col = 0; row++; }
            }
        }

        // Second pass: create transitions and events
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element el)) continue;
            String nodeName = el.getAttribute("name");
            FlowNode sourceNode = nodesByName.get(nodeName);
            if (sourceNode == null) continue;

            NodeList nodeChildren = el.getChildNodes();
            for (int j = 0; j < nodeChildren.getLength(); j++) {
                if (!(nodeChildren.item(j) instanceof Element child)) continue;
                String childTag = child.getLocalName() != null ? child.getLocalName() : child.getTagName();
                switch (childTag) {
                    case "transition" -> {
                        String transName = child.getAttribute("name");
                        String toName = child.getAttribute("to");
                        FlowNode target = nodesByName.get(toName);
                        if (target != null) {
                            Transition t = new Transition("t_" + i + "_" + j, transName, sourceNode, target);
                            String condition = getChildText(child, "condition");
                            if (condition != null) t.setCondition(condition);
                            Element actionEl = getChildElement(child, "action");
                            if (actionEl != null) {
                                t.setActionClass(actionEl.getAttribute("class"));
                                t.setActionExpression(actionEl.getAttribute("expression"));
                            }
                            process.addTransition(t);
                        }
                    }
                    case "event" -> {
                        String eventType = child.getAttribute("type");
                        if (!eventType.isBlank()) {
                            NodeEvent ev = sourceNode.getOrCreateEvent(eventType);
                            NodeList actionEls = child.getChildNodes();
                            for (int k = 0; k < actionEls.getLength(); k++) {
                                if (!(actionEls.item(k) instanceof Element ae)) continue;
                                String aeTag = ae.getLocalName() != null ? ae.getLocalName() : ae.getTagName();
                                if ("action".equals(aeTag)) {
                                    ev.addAction(ae.getAttribute("expression"), ae.getAttribute("class"));
                                }
                            }
                        }
                    }
                }
            }
        }
        return process;
    }

    private TaskNode buildTaskNode(String id, String name, int x, int y, Element el) {
        TaskNode tn = new TaskNode(id, name, x, y);
        Element taskEl = getChildElement(el, "task");
        if (taskEl != null) {
            tn.setSwimlane(taskEl.getAttribute("swimlane"));
            tn.setActorId(taskEl.getAttribute("actor-id"));
            tn.setDescription(getChildText(taskEl, "description"));
            Element assignEl = getChildElement(taskEl, "assignment");
            if (assignEl != null) {
                tn.setAssignmentClass(assignEl.getAttribute("class"));
                tn.setAssignmentExpression(assignEl.getAttribute("expression"));
            }
        }
        return tn;
    }

    private DecisionNode buildDecision(String id, String name, int x, int y, Element el) {
        DecisionNode dn = new DecisionNode(id, name, x, y);
        dn.setExpression(el.getAttribute("expression"));
        dn.setDecisionClass(el.getAttribute("class"));
        return dn;
    }

    private ActionNode buildActionNode(String id, String name, int x, int y, Element el) {
        ActionNode an = new ActionNode(id, name, x, y);
        Element actionEl = getChildElement(el, "action");
        if (actionEl != null) {
            an.setActionClass(actionEl.getAttribute("class"));
            an.setActionExpression(actionEl.getAttribute("expression"));
        }
        return an;
    }

    private Element getChildElement(Element parent, String localName) {
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i) instanceof Element e) {
                String tag = e.getLocalName() != null ? e.getLocalName() : e.getTagName();
                if (localName.equals(tag)) return e;
            }
        }
        return null;
    }

    private String getChildText(Element parent, String localName) {
        Element el = getChildElement(parent, localName);
        return el != null ? el.getTextContent().trim() : null;
    }
}
