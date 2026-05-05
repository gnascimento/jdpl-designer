package br.com.caffeineti.ui;

import br.com.caffeineti.model.*;
import br.com.caffeineti.i18n.Messages;
import br.com.caffeineti.seam.SeamBean;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.Consumer;

/** Right-side panel that shows and edits properties of the selected element. */
public class PropertiesPanel extends JPanel {

    public interface ChangeListener {
        void onPropertiesChanged();
    }

    private ChangeListener changeListener;
    private boolean firing = false;   // guard against recursive fires
    private JPanel contentPanel;
    private JScrollPane scrollPane;
    // Reference to process needed for swimlane autocomplete and events dialog owner
    private java.awt.Frame ownerFrame;
    private br.com.caffeineti.model.ProcessDefinition process;
    private List<SeamBean> seamBeans = List.of();

    public PropertiesPanel() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(260, 0));
        setBackground(new Color(0xFAFAFA));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(0xCFD8DC)));

        JLabel title = new JLabel(Messages.get("properties.title"));
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(new Color(0x37474F));
        title.setBorder(new EmptyBorder(10, 12, 8, 12));
        add(title, BorderLayout.NORTH);

        contentPanel = new JPanel();
        contentPanel.setBackground(new Color(0xFAFAFA));
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        scrollPane = new JScrollPane(contentPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        showEmpty();
    }

    public void setChangeListener(ChangeListener l) { this.changeListener = l; }
    public void setOwnerFrame(java.awt.Frame f) { this.ownerFrame = f; }
    public void setProcess(br.com.caffeineti.model.ProcessDefinition p) { this.process = p; }
    public void setSeamBeans(List<SeamBean> beans) { this.seamBeans = beans == null ? List.of() : List.copyOf(beans); }

    public void showEmpty() {
        contentPanel.removeAll();
        JLabel lbl = new JLabel(Messages.get("properties.empty"));
        lbl.setForeground(new Color(0x90A4AE));
        lbl.setFont(new Font("SansSerif", Font.ITALIC, 12));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        lbl.setBorder(new EmptyBorder(16, 12, 0, 12));
        contentPanel.add(lbl);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    public void showNode(FlowNode node) {
        contentPanel.removeAll();

        // Type badge
        JLabel typeBadge = new JLabel(node.getType().getDisplayName());
        typeBadge.setFont(new Font("SansSerif", Font.BOLD, 10));
        typeBadge.setForeground(Color.WHITE);
        typeBadge.setOpaque(true);
        typeBadge.setBackground(getTypeColor(node.getType()));
        typeBadge.setBorder(new EmptyBorder(2, 8, 2, 8));
        typeBadge.setAlignmentX(Component.LEFT_ALIGNMENT);
        addRow(typeBadge);
        addSeparator();

        addField("field.id", node.getId(), null, false);
        addField("field.name", node.getName(), val -> {
            node.setName(val); fire();
        }, true);

        // Type-specific fields
        switch (node.getType()) {
            case TASK_NODE -> {
                TaskNode tn = (TaskNode) node;
                addSection("section.task");
                addSwimlaneField(tn);
                addField("field.actorId",    tn.getActorId(),    val -> { tn.setActorId(val);    fire(); }, true);
                addField("field.description", tn.getDescription(),val -> { tn.setDescription(val);fire(); }, true);
                addSection("section.assignment");
                addField("field.class",       tn.getAssignmentClass(),      val -> { tn.setAssignmentClass(val);      fire(); }, true);
                addField("field.expression",  tn.getAssignmentExpression(), val -> { tn.setAssignmentExpression(val); fire(); }, true);
                addSection("section.events");
                addEventsButton(node);
            }
            case DECISION -> {
                DecisionNode dn = (DecisionNode) node;
                addSection("section.decision");
                addField("field.expression", dn.getExpression(),    val -> { dn.setExpression(val);    fire(); }, true);
                addField("field.class",      dn.getDecisionClass(), val -> { dn.setDecisionClass(val); fire(); }, true);
            }
            case ACTION_NODE -> {
                ActionNode an = (ActionNode) node;
                addSection("section.actionHandler");
                addField("field.class",      an.getActionClass(),       val -> { an.setActionClass(val);       fire(); }, true);
                addField("field.expression", an.getActionExpression(),  val -> { an.setActionExpression(val);  fire(); }, true);
                addSection("section.events");
                addEventsButton(node);
            }
            default -> {}
        }

        contentPanel.add(Box.createVerticalGlue());
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    public void showTransition(Transition t) {
        contentPanel.removeAll();

        JLabel typeBadge = new JLabel(Messages.get("badge.transition"));
        typeBadge.setFont(new Font("SansSerif", Font.BOLD, 10));
        typeBadge.setForeground(Color.WHITE);
        typeBadge.setOpaque(true);
        typeBadge.setBackground(new Color(0x546E7A));
        typeBadge.setBorder(new EmptyBorder(2, 8, 2, 8));
        typeBadge.setAlignmentX(Component.LEFT_ALIGNMENT);
        addRow(typeBadge);
        addSeparator();

        addField("field.from", t.getSource().getName(), null, false);
        addField("field.to",   t.getTarget().getName(), null, false);
        addField("field.name", t.getName(), val -> { t.setName(val); fire(); }, true);

        addSection("section.condition");
        addField("field.expression", t.getCondition(), val -> { t.setCondition(val); fire(); }, true);

        addSection("section.actionHandler");
        addField("field.class",      t.getActionClass(),      val -> { t.setActionClass(val);      fire(); }, true);
        addField("field.expression", t.getActionExpression(), val -> { t.setActionExpression(val); fire(); }, true);

        contentPanel.add(Box.createVerticalGlue());
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    /** Populate EL expression field with a Seam/CDI bean method. */
    public void injectBeanMethod(SeamBean bean, String method) {
        // Find expression fields and update
        String expr = bean.getMethodExpression(method);
        for (Component c : contentPanel.getComponents()) {
            if (c instanceof JPanel row) {
                for (Component rc : row.getComponents()) {
                    if (rc instanceof JTextField tf && tf.getClientProperty("fieldType") != null) {
                        String ft = (String) tf.getClientProperty("fieldType");
                        if ("field.expression".equals(ft) || "Expression".equals(ft) || "expression".equalsIgnoreCase(ft)) {
                            tf.setText(expr);
                            tf.postActionEvent();
                            break;
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Specialised field builders
    // -------------------------------------------------------------------------

    /** Swimlane field: JComboBox editable, populated from process swimlane list. */
    private void addSwimlaneField(TaskNode tn) {
        JPanel row = new JPanel(new BorderLayout(4, 2));
        row.setBackground(new Color(0xFAFAFA));
        row.setBorder(new EmptyBorder(4, 12, 4, 12));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        JLabel lbl = new JLabel(Messages.get("field.swimlane"));
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lbl.setForeground(new Color(0x78909C));
        row.add(lbl, BorderLayout.NORTH);

        // Populate combo with all known swimlane names
        List<String> lanes = process != null ? process.getAllSwimlaneNames() : List.of();
        // Add a blank entry at the top so the user can clear the swimlane
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement("");
        for (String l : lanes) model.addElement(l);

        JComboBox<String> combo = new JComboBox<>(model);
        combo.setEditable(true);
        combo.setFont(new Font("SansSerif", Font.PLAIN, 12));

        String cur = tn.getSwimlane();
        combo.setSelectedItem(cur != null ? cur : "");

        // Reads the current editor text and applies it to the model (always fires)
        Runnable apply = () -> {
            Object item = combo.getEditor().getItem();
            String val = item == null ? "" : item.toString().trim();
            tn.setSwimlane(val.isEmpty() ? null : val);
            fire();
        };

        // ItemListener: fires only on actual user selection from dropdown (SELECTED only)
        combo.addItemListener(e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                SwingUtilities.invokeLater(apply);
            }
        });

        // focusLost: fires when user types something and clicks away without Enter
        if (combo.getEditor().getEditorComponent() instanceof JTextField editorField) {
            TextFieldEditingSupport.install(editorField);
            editorField.addActionListener(e -> apply.run());   // Enter key
            editorField.addFocusListener(new FocusAdapter() {
                @Override public void focusLost(FocusEvent e) { apply.run(); }
            });
        }

        row.add(combo, BorderLayout.CENTER);
        contentPanel.add(row);
    }

    /** "Editar Eventos..." button that opens the EventsEditorDialog. */
    private void addEventsButton(FlowNode node) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        row.setBackground(new Color(0xFAFAFA));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        int count = node.getEvents().stream().mapToInt(e -> e.getActions().size()).sum();
        JButton btn = new JButton(Messages.get("properties.eventsButton", count));
        btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btn.setFocusPainted(false);
        btn.addActionListener(e -> {
            EventsEditorDialog dlg = new EventsEditorDialog(ownerFrame, node, seamBeans, () -> {
                fire();
                // Refresh button label
                int c = node.getEvents().stream().mapToInt(ev -> ev.getActions().size()).sum();
                btn.setText(Messages.get("properties.eventsButton", c));
            });
            dlg.setVisible(true);
        });
        row.add(btn);
        contentPanel.add(row);
    }

    // -------------------------------------------------------------------------
    // Generic builder helpers
    // -------------------------------------------------------------------------

    private void addField(String labelKey, String value, Consumer<String> onEdit, boolean editable) {
        JPanel row = new JPanel(new BorderLayout(4, 2));
        row.setBackground(new Color(0xFAFAFA));
        row.setBorder(new EmptyBorder(4, 12, 4, 12));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        JLabel lbl = new JLabel(Messages.get(labelKey));
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lbl.setForeground(new Color(0x78909C));
        row.add(lbl, BorderLayout.NORTH);

        JTextField tf = new JTextField(value == null ? "" : value);
        TextFieldEditingSupport.install(tf);
        tf.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tf.setEditable(editable);
        tf.setBackground(editable ? Color.WHITE : new Color(0xF0F0F0));
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xCFD8DC)),
            new EmptyBorder(2, 6, 2, 6)));
        tf.putClientProperty("fieldType", labelKey);
        if (editable && "field.expression".equals(labelKey)) {
            ElExpressionAutoComplete.install(tf, () -> seamBeans);
        }

        if (onEdit != null) {
            tf.addActionListener(e -> onEdit.accept(tf.getText()));
            tf.addFocusListener(new FocusAdapter() {
                @Override public void focusLost(FocusEvent e) { onEdit.accept(tf.getText()); }
            });
        }
        row.add(tf, BorderLayout.CENTER);
        contentPanel.add(row);
    }

    private void addSection(String titleKey) {
        JLabel lbl = new JLabel(Messages.get(titleKey).toUpperCase(Messages.getLocale()));
        lbl.setFont(new Font("SansSerif", Font.BOLD, 10));
        lbl.setForeground(new Color(0x90A4AE));
        lbl.setBorder(new EmptyBorder(10, 12, 2, 12));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(lbl);
    }

    private void addSeparator() {
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(0xE0E0E0));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        contentPanel.add(sep);
    }

    private void addRow(Component c) {
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        wrapper.setBackground(new Color(0xFAFAFA));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(c);
        contentPanel.add(wrapper);
    }

    private Color getTypeColor(NodeType type) {
        return switch (type) {
            case START_STATE -> NodeRenderer.C_START;
            case END_STATE   -> NodeRenderer.C_END;
            case TASK_NODE   -> NodeRenderer.C_TASK;
            case STATE       -> NodeRenderer.C_STATE;
            case DECISION    -> NodeRenderer.C_DECISION;
            case FORK, JOIN  -> NodeRenderer.C_FORK_JOIN;
            case ACTION_NODE -> NodeRenderer.C_ACTION;
        };
    }

    private void fire() {
        if (firing || changeListener == null) return;
        firing = true;
        try { changeListener.onPropertiesChanged(); }
        finally { firing = false; }
    }
}
