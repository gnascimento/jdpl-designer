package br.com.caffeineti.ui;

import br.com.caffeineti.model.FlowNode;
import br.com.caffeineti.i18n.Messages;
import br.com.caffeineti.model.NodeEvent;
import br.com.caffeineti.model.NodeType;
import br.com.caffeineti.seam.SeamBean;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.List;

/**
 * Modal dialog for editing jPDL {@code <event>} elements on a node.
 *
 * Left pane : list of event types (node-enter, task-create, etc.)
 * Right pane: list of {@code <action>} entries for the selected event
 *             – each action has an EL expression and/or a handler class.
 */
public class EventsEditorDialog extends JDialog {

    private final FlowNode node;
    private final List<SeamBean> seamBeans;
    private Runnable onChange;

    // ---- Left: event types ----
    private final DefaultListModel<NodeEvent> eventsModel = new DefaultListModel<>();
    private final JList<NodeEvent>            eventsList  = new JList<>(eventsModel);

    // ---- Right: actions for selected event ----
    private final DefaultListModel<NodeEvent.ActionEntry> actionsModel = new DefaultListModel<>();
    private final JList<NodeEvent.ActionEntry>             actionsList  = new JList<>(actionsModel);

    public EventsEditorDialog(Frame owner, FlowNode node, List<SeamBean> seamBeans, Runnable onChange) {
        super(owner, Messages.get("events.title", node.getName()), false); // non-modal so canvas stays accessible
        this.node = node;
        this.seamBeans = seamBeans == null ? List.of() : List.copyOf(seamBeans);
        this.onChange = onChange;

        setSize(780, 480);
        setMinimumSize(new Dimension(600, 360));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(new EmptyBorder(10, 10, 10, 10));

        // ---- Header ----
        String[] availableTypes = (node.getType() == NodeType.TASK_NODE)
                ? NodeEvent.TASK_ONLY_TYPES : NodeEvent.ALL_TYPES;

        JLabel header = new JLabel(Messages.get("events.header", node.getName(), node.getType().getDisplayName()));
        header.setFont(new Font("SansSerif", Font.BOLD, 13));
        header.setForeground(new Color(0x37474F));
        add(header, BorderLayout.NORTH);

        // ---- Main split ----
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerLocation(260);
        split.setResizeWeight(0.35);
        split.setBorder(null);

        // -- Left panel --
        JPanel leftPanel = new JPanel(new BorderLayout(4, 4));
        leftPanel.setBorder(new TitledBorder(Messages.get("events.types")));

        eventsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        eventsList.setFont(new Font("SansSerif", Font.PLAIN, 12));
        eventsList.setFixedCellHeight(26);
        leftPanel.add(new JScrollPane(eventsList), BorderLayout.CENTER);

        JPanel evBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JComboBox<String> typeCombo = new JComboBox<>(availableTypes);
        typeCombo.setFont(new Font("SansSerif", Font.PLAIN, 11));
        JButton addEvBtn = smallBtn(Messages.get("events.add"));
        JButton remEvBtn = smallBtn(Messages.get("events.remove"));
        evBtns.add(typeCombo); evBtns.add(addEvBtn); evBtns.add(remEvBtn);
        leftPanel.add(evBtns, BorderLayout.SOUTH);

        split.setLeftComponent(leftPanel);

        // -- Right panel --
        JPanel rightPanel = new JPanel(new BorderLayout(4, 4));
        rightPanel.setBorder(new TitledBorder(Messages.get("events.actions")));

        actionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        actionsList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        actionsList.setFixedCellHeight(28);
        actionsList.setCellRenderer(new ActionCellRenderer());
        rightPanel.add(new JScrollPane(actionsList), BorderLayout.CENTER);

        JPanel acBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton addAcBtn  = smallBtn(Messages.get("events.add"));
        JButton editAcBtn = smallBtn(Messages.get("events.edit"));
        JButton remAcBtn  = smallBtn(Messages.get("events.remove"));
        JButton upAcBtn   = smallBtn("↑"); JButton downAcBtn = smallBtn("↓");
        acBtns.add(addAcBtn); acBtns.add(editAcBtn); acBtns.add(remAcBtn);
        acBtns.add(Box.createHorizontalStrut(10)); acBtns.add(upAcBtn); acBtns.add(downAcBtn);
        rightPanel.add(acBtns, BorderLayout.SOUTH);

        split.setRightComponent(rightPanel);
        add(split, BorderLayout.CENTER);

        // ---- Bottom ----
        JButton closeBtn = new JButton(Messages.get("events.close"));
        closeBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        closeBtn.addActionListener(e -> dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(closeBtn);
        add(bottom, BorderLayout.SOUTH);

        // ---- Populate from node ----
        node.getEvents().forEach(eventsModel::addElement);

        // ---- Wire listeners ----

        eventsList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            actionsModel.clear();
            NodeEvent ev = eventsList.getSelectedValue();
            if (ev != null) ev.getActions().forEach(actionsModel::addElement);
        });

        addEvBtn.addActionListener(e -> {
            String type = (String) typeCombo.getSelectedItem();
            if (type == null) return;
            // Only one event per type
            for (int i = 0; i < eventsModel.size(); i++) {
                if (eventsModel.get(i).getEventType().equals(type)) {
                    eventsList.setSelectedIndex(i); return;
                }
            }
            NodeEvent ev = node.getOrCreateEvent(type);
            eventsModel.addElement(ev);
            eventsList.setSelectedIndex(eventsModel.size() - 1);
            fireChange();
        });

        remEvBtn.addActionListener(e -> {
            int idx = eventsList.getSelectedIndex();
            if (idx < 0) return;
            NodeEvent ev = eventsModel.get(idx);
            node.getEvents().remove(ev);
            eventsModel.remove(idx);
            actionsModel.clear();
            fireChange();
        });

        addAcBtn.addActionListener(e -> {
            NodeEvent ev = eventsList.getSelectedValue();
            if (ev == null) { JOptionPane.showMessageDialog(this, Messages.get("events.selectTypeFirst")); return; }
            ActionEntryDialog dlg = new ActionEntryDialog(this, null, seamBeans);
            dlg.setVisible(true);
            NodeEvent.ActionEntry entry = dlg.getResult();
            if (entry != null) {
                ev.getActions().add(entry);
                actionsModel.addElement(entry);
                fireChange();
            }
        });

        editAcBtn.addActionListener(e -> {
            int idx = actionsList.getSelectedIndex();
            if (idx < 0) return;
            NodeEvent.ActionEntry existing = actionsModel.get(idx);
            ActionEntryDialog dlg = new ActionEntryDialog(this, existing, seamBeans);
            dlg.setVisible(true);
            NodeEvent.ActionEntry updated = dlg.getResult();
            if (updated != null) {
                existing.setExpression(updated.getExpression());
                existing.setHandlerClass(updated.getHandlerClass());
                // Refresh list
                actionsModel.set(idx, existing);
                fireChange();
            }
        });

        actionsList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) editAcBtn.doClick();
            }
        });

        remAcBtn.addActionListener(e -> {
            NodeEvent ev = eventsList.getSelectedValue();
            int idx = actionsList.getSelectedIndex();
            if (ev == null || idx < 0) return;
            ev.getActions().remove(idx);
            actionsModel.remove(idx);
            fireChange();
        });

        upAcBtn.addActionListener(e -> moveAction(-1));
        downAcBtn.addActionListener(e -> moveAction(+1));
    }

    private void moveAction(int delta) {
        NodeEvent ev = eventsList.getSelectedValue();
        int idx = actionsList.getSelectedIndex();
        if (ev == null || idx < 0) return;
        int newIdx = idx + delta;
        if (newIdx < 0 || newIdx >= ev.getActions().size()) return;
        NodeEvent.ActionEntry item = ev.getActions().remove(idx);
        ev.getActions().add(newIdx, item);
        actionsModel.remove(idx);
        actionsModel.insertElementAt(item, newIdx);
        actionsList.setSelectedIndex(newIdx);
        fireChange();
    }

    private void fireChange() { if (onChange != null) onChange.run(); }

    private JButton smallBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.PLAIN, 11));
        b.setFocusPainted(false);
        b.setMargin(new Insets(1, 6, 1, 6));
        return b;
    }

    // =========================================================================
    // Action entry cell renderer
    // =========================================================================

    private static class ActionCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof NodeEvent.ActionEntry ae) {
                String txt = (ae.getExpression() != null && !ae.getExpression().isBlank())
                        ? ae.getExpression()
                        : (ae.getHandlerClass() != null ? "class: " + ae.getHandlerClass() : Messages.get("events.empty"));
                setText((index + 1) + ". " + txt);
                setFont(new Font("Monospaced", Font.PLAIN, 11));
                setToolTipText(txt);
            }
            return this;
        }
    }

    // =========================================================================
    // Sub-dialog for editing a single ActionEntry
    // =========================================================================

    private static class ActionEntryDialog extends JDialog {
        private NodeEvent.ActionEntry result;

        ActionEntryDialog(Dialog owner, NodeEvent.ActionEntry existing, List<SeamBean> seamBeans) {
            super(owner, Messages.get("events.editAction"), true);
            setSize(600, 200);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout(8, 8));
            getRootPane().setBorder(new EmptyBorder(12, 12, 8, 12));

            JLabel exprLabel = lbl(Messages.get("events.elExpression"));
            JTextField exprField = new JTextField(existing != null ? nvl(existing.getExpression()) : "");
            exprField.setFont(new Font("Monospaced", Font.PLAIN, 12));
            ElExpressionAutoComplete.install(exprField, () -> seamBeans);

            JLabel classLabel = lbl(Messages.get("events.handlerClass"));
            JTextField classField = new JTextField(existing != null ? nvl(existing.getHandlerClass()) : "");
            TextFieldEditingSupport.install(classField);
            classField.setFont(new Font("SansSerif", Font.PLAIN, 12));

            JPanel form = new JPanel(new GridLayout(4, 1, 4, 4));
            form.add(exprLabel); form.add(exprField);
            form.add(classLabel); form.add(classField);
            add(form, BorderLayout.CENTER);

            JButton ok  = new JButton("OK");
            JButton cancel = new JButton(Messages.get("events.cancel"));
            ok.addActionListener(e -> {
                result = new NodeEvent.ActionEntry(
                        exprField.getText().trim(),
                        classField.getText().trim());
                dispose();
            });
            cancel.addActionListener(e -> dispose());
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            btns.add(cancel); btns.add(ok);
            add(btns, BorderLayout.SOUTH);

            // Press Enter in expression field → OK
            exprField.addActionListener(e -> ok.doClick());
        }

        private JLabel lbl(String text) {
            JLabel l = new JLabel(text);
            l.setFont(new Font("SansSerif", Font.PLAIN, 11));
            l.setForeground(new Color(0x546E7A));
            return l;
        }

        private String nvl(String s) { return s != null ? s : ""; }

        NodeEvent.ActionEntry getResult() { return result; }
    }
}
