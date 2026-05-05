package br.com.caffeineti.ui;

import br.com.caffeineti.model.ProcessDefinition;
import br.com.caffeineti.i18n.Messages;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Dialog for managing the ordered list of swimlane names in a process.
 * Swimlanes define the horizontal bands visible in the diagram canvas.
 */
public class SwimlanesDialog extends JDialog {

    private final ProcessDefinition process;
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> list = new JList<>(listModel);

    public SwimlanesDialog(Frame owner, ProcessDefinition process) {
        super(owner, Messages.get("swimlanes.title"), true);
        this.process = process;

        // Populate from process
        process.getAllSwimlaneNames().forEach(listModel::addElement);

        setSize(380, 400);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(new EmptyBorder(10, 10, 10, 10));

        // ---- List ----
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(new Font("SansSerif", Font.PLAIN, 13));
        list.setFixedCellHeight(28);
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createLineBorder(new Color(0xCFD8DC)));
        add(sp, BorderLayout.CENTER);

        // ---- Buttons ----
        JPanel btnPanel = new JPanel(new GridLayout(1, 4, 6, 0));
        btnPanel.setOpaque(false);

        JButton addBtn = btn(Messages.get("swimlanes.add"));
        JButton remBtn = btn(Messages.get("swimlanes.remove"));
        JButton upBtn  = btn("↑ " + Messages.get("swimlanes.up"));
        JButton downBtn= btn("↓ " + Messages.get("swimlanes.down"));

        addBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, Messages.get("swimlanes.newPrompt"), Messages.get("swimlanes.newTitle"),
                    JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.isBlank()) {
                String trimmed = name.trim();
                if (!listContains(trimmed)) {
                    listModel.addElement(trimmed);
                    list.setSelectedIndex(listModel.size() - 1);
                }
            }
        });

        remBtn.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx >= 0) listModel.remove(idx);
        });

        upBtn.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx > 0) {
                String item = listModel.remove(idx);
                listModel.insertElementAt(item, idx - 1);
                list.setSelectedIndex(idx - 1);
            }
        });

        downBtn.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx >= 0 && idx < listModel.size() - 1) {
                String item = listModel.remove(idx);
                listModel.insertElementAt(item, idx + 1);
                list.setSelectedIndex(idx + 1);
            }
        });

        btnPanel.add(addBtn); btnPanel.add(remBtn);
        btnPanel.add(upBtn);  btnPanel.add(downBtn);
        add(btnPanel, BorderLayout.SOUTH);

        // ---- Top label ----
        JLabel lbl = new JLabel(Messages.get("swimlanes.description"));
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lbl.setBorder(new EmptyBorder(0, 0, 4, 0));
        add(lbl, BorderLayout.NORTH);

        // On close: save back to process
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { save(); }
        });

        JButton closeBtn = btn(Messages.get("swimlanes.close"));
        closeBtn.addActionListener(e -> { save(); dispose(); });
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        south.setOpaque(false);
        south.add(closeBtn);
        // Replace the grid-layout panel with a split layout
        JPanel southWrapper = new JPanel(new BorderLayout(0, 6));
        southWrapper.setOpaque(false);
        southWrapper.add(btnPanel, BorderLayout.NORTH);
        southWrapper.add(south,    BorderLayout.SOUTH);
        remove(btnPanel);
        add(southWrapper, BorderLayout.SOUTH);
    }

    private void save() {
        process.getSwimlaneNames().clear();
        for (int i = 0; i < listModel.size(); i++) {
            process.getSwimlaneNames().add(listModel.get(i));
        }
    }

    private boolean listContains(String name) {
        for (int i = 0; i < listModel.size(); i++) if (listModel.get(i).equals(name)) return true;
        return false;
    }

    private JButton btn(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.PLAIN, 12));
        b.setFocusPainted(false);
        return b;
    }
}
