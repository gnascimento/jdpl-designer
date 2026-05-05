package br.com.caffeineti.ui;

import br.com.caffeineti.model.ProcessDefinition;
import br.com.caffeineti.i18n.Messages;
import br.com.caffeineti.xml.JpdlSerializer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * XML preview panel with syntax highlighting, line numbers, find bar, and pop-out window.
 * Designed to live inside a JTabbedPane in the main layout.
 */
public class XmlViewPanel extends JPanel {

    private final JTextPane  textPane;
    private final JTextArea  lineNumbers;
    private final JpdlSerializer serializer = new JpdlSerializer();
    private ProcessDefinition process;
    private String currentXml = "";

    /** Extra panes inside detached popup windows; cleaned up on close. */
    private final List<JTextPane> extraPanes = new ArrayList<>();

    // Search state
    private final JPanel     searchBar;
    private JTextField       searchField;
    private JLabel           matchLabel;
    private final List<int[]> matches = new ArrayList<>();
    private int matchIndex = -1;

    // Colors
    private static final Color C_TAG       = new Color(0x2196F3);
    private static final Color C_ATTR_KEY  = new Color(0x7B1FA2);
    private static final Color C_ATTR_VAL  = new Color(0x388E3C);
    private static final Color C_COMMENT   = new Color(0x9E9E9E);
    private static final Color C_DECL      = new Color(0x795548);
    private static final Color BG_DARK     = new Color(0x1E1E2E);
    private static final Color FG_DEFAULT  = new Color(0xCDD6F4);
    private static final Color BG_GUTTER   = new Color(0x181825);
    private static final Color FG_GUTTER   = new Color(0x6C7086);
    private static final Color MATCH_BG    = new Color(0xF9E2AF);
    private static final Color MATCH_CUR   = new Color(0xFAB387);

    public XmlViewPanel(ProcessDefinition process) {
        this.process = process;
        setLayout(new BorderLayout());

        // ---- Toolbar (find + pop-out buttons) ----
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        toolbar.setBackground(new Color(0xECEFF1));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xCFD8DC)));

        JButton findBtn = new JButton(Messages.get("xml.find"));
        findBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        findBtn.setFocusPainted(false);
        findBtn.setMargin(new Insets(2, 8, 2, 8));
        findBtn.addActionListener(e -> showSearch());

        JButton popOutBtn = new JButton(Messages.get("xml.popOut"));
        popOutBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        popOutBtn.setFocusPainted(false);
        popOutBtn.setMargin(new Insets(2, 8, 2, 8));
        popOutBtn.addActionListener(e -> showPopup());

        toolbar.add(findBtn);
        toolbar.add(popOutBtn);
        add(toolbar, BorderLayout.NORTH);

        // ---- Text pane — fill viewport when content is narrower; scroll when wider ----
        textPane = new JTextPane() {
            @Override public boolean getScrollableTracksViewportWidth() {
                return getUI().getPreferredSize(this).width <= getParent().getSize().width;
            }
        };
        textPane.setEditable(false);
        textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN,12));
        textPane.setBackground(BG_DARK);
        textPane.setForeground(FG_DEFAULT);
        textPane.setCaretColor(FG_DEFAULT);

        // ---- Line-number gutter ----
        lineNumbers = createGutter();

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setRowHeaderView(lineNumbers);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // ---- Find bar (hidden initially) ----
        searchBar = buildSearchBar();
        searchBar.setVisible(false);

        // Ctrl+F on the text pane opens the search bar
        textPane.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "find");
        textPane.getActionMap().put("find", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { showSearch(); }
        });

        JPanel center = new JPanel(new BorderLayout());
        center.add(searchBar, BorderLayout.NORTH);
        center.add(scrollPane, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public void setProcess(ProcessDefinition process) {
        this.process = process;
        refresh();
    }

    public void refresh() {
        if (process == null) return;
        currentXml = serializer.serialize(process);
        applyHighlighting(currentXml, textPane);
        updateGutter(currentXml, lineNumbers);
        if (!matches.isEmpty()) updateMatches(); // re-apply search highlights

        extraPanes.removeIf(p -> !p.isDisplayable());
        for (JTextPane pane : extraPanes) {
            applyHighlighting(currentXml, pane);
            if (pane.getClientProperty("gutter") instanceof JTextArea g) updateGutter(currentXml, g);
        }
    }

    // =========================================================================
    // Pop-out window
    // =========================================================================

    private void showPopup() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(owner, Messages.get("xml.popOutTitle"), Dialog.ModalityType.MODELESS);
        dlg.setSize(1000, 750);
        dlg.setLocationRelativeTo(owner);

        JTextPane popPane = new JTextPane() {
            @Override public boolean getScrollableTracksViewportWidth() {
                return getUI().getPreferredSize(this).width <= getParent().getSize().width;
            }
        };
        popPane.setEditable(false);
        popPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        popPane.setBackground(BG_DARK);
        popPane.setForeground(FG_DEFAULT);

        JTextArea popGutter = createGutter();
        popPane.putClientProperty("gutter", popGutter);

        JScrollPane sp = new JScrollPane(popPane);
        sp.setRowHeaderView(popGutter);
        sp.getVerticalScrollBar().setUnitIncrement(16);

        if (!currentXml.isEmpty()) {
            applyHighlighting(currentXml, popPane);
            updateGutter(currentXml, popGutter);
        }

        extraPanes.add(popPane);

        dlg.getContentPane().add(sp, BorderLayout.CENTER);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dlg.setVisible(true);
    }

    // =========================================================================
    // Find / Search
    // =========================================================================

    private JPanel buildSearchBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        bar.setBackground(new Color(0x313244));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x45475A)));

        JLabel lbl = new JLabel(Messages.get("xml.findLabel"));
        lbl.setForeground(FG_DEFAULT);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 11));

        searchField = new JTextField(24);
        searchField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        searchField.addActionListener(e -> findNext());

        JButton prevBtn = new JButton("◀");
        JButton nextBtn = new JButton("▶");
        for (JButton b : new JButton[]{prevBtn, nextBtn}) {
            b.setFont(new Font("SansSerif", Font.PLAIN, 10));
            b.setFocusPainted(false);
            b.setMargin(new Insets(1, 5, 1, 5));
        }
        prevBtn.addActionListener(e -> findPrev());
        nextBtn.addActionListener(e -> findNext());

        matchLabel = new JLabel("");
        matchLabel.setForeground(FG_GUTTER);
        matchLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        matchLabel.setBorder(new EmptyBorder(0, 6, 0, 6));

        JButton closeBtn = new JButton("✕");
        closeBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));
        closeBtn.setFocusPainted(false);
        closeBtn.setMargin(new Insets(1, 5, 1, 5));
        closeBtn.addActionListener(e -> hideSearch());

        // Update matches live as user types
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateMatches(); }
            public void removeUpdate(DocumentEvent e) { updateMatches(); }
            public void changedUpdate(DocumentEvent e) {}
        });

        // Escape closes the bar
        searchField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        searchField.getActionMap().put("close", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { hideSearch(); }
        });

        bar.add(lbl);
        bar.add(searchField);
        bar.add(prevBtn);
        bar.add(nextBtn);
        bar.add(matchLabel);
        bar.add(closeBtn);
        return bar;
    }

    private void showSearch() {
        searchBar.setVisible(true);
        searchField.selectAll();
        searchField.requestFocusInWindow();
    }

    private void hideSearch() {
        searchBar.setVisible(false);
        textPane.getHighlighter().removeAllHighlights();
        matches.clear();
        matchIndex = -1;
        matchLabel.setText("");
        textPane.requestFocusInWindow();
    }

    private void updateMatches() {
        textPane.getHighlighter().removeAllHighlights();
        matches.clear();
        matchIndex = -1;

        String query = searchField.getText();
        if (query.isEmpty()) { matchLabel.setText(""); return; }

        String lowerXml   = currentXml.toLowerCase();
        String lowerQuery = query.toLowerCase();
        int idx = 0;
        while ((idx = lowerXml.indexOf(lowerQuery, idx)) >= 0) {
            matches.add(new int[]{idx, idx + query.length()});
            idx += query.length();
        }

        Highlighter.HighlightPainter painter =
                new DefaultHighlighter.DefaultHighlightPainter(MATCH_BG);
        for (int[] m : matches) {
            try { textPane.getHighlighter().addHighlight(m[0], m[1], painter); }
            catch (BadLocationException ignored) {}
        }

        if (!matches.isEmpty()) { matchIndex = 0; scrollToMatch(0); }
        updateMatchLabel();
    }

    private void findNext() {
        if (matches.isEmpty()) { updateMatches(); return; }
        matchIndex = (matchIndex + 1) % matches.size();
        scrollToMatch(matchIndex);
        updateMatchLabel();
    }

    private void findPrev() {
        if (matches.isEmpty()) { updateMatches(); return; }
        matchIndex = (matchIndex - 1 + matches.size()) % matches.size();
        scrollToMatch(matchIndex);
        updateMatchLabel();
    }

    private void scrollToMatch(int idx) {
        if (idx < 0 || idx >= matches.size()) return;
        int[] m = matches.get(idx);

        // Re-paint all: normal highlights + current match as brighter color
        textPane.getHighlighter().removeAllHighlights();
        Highlighter.HighlightPainter normal  = new DefaultHighlighter.DefaultHighlightPainter(MATCH_BG);
        Highlighter.HighlightPainter current = new DefaultHighlighter.DefaultHighlightPainter(MATCH_CUR);
        for (int i = 0; i < matches.size(); i++) {
            int[] mm = matches.get(i);
            try { textPane.getHighlighter().addHighlight(mm[0], mm[1], i == idx ? current : normal); }
            catch (BadLocationException ignored) {}
        }

        try {
            Rectangle r = textPane.modelToView2D(m[0]).getBounds();
            textPane.scrollRectToVisible(r);
            textPane.setCaretPosition(m[0]);
        } catch (BadLocationException ignored) {}
    }

    private void updateMatchLabel() {
        if (matches.isEmpty()) {
            matchLabel.setForeground(new Color(0xF38BA8)); // red-ish
            matchLabel.setText(Messages.get("xml.noMatches"));
        } else {
            matchLabel.setForeground(FG_GUTTER);
            matchLabel.setText((matchIndex + 1) + " / " + matches.size());
        }
    }

    // =========================================================================
    // Line-number gutter
    // =========================================================================

    private static JTextArea createGutter() {
        JTextArea g = new JTextArea("1");
        g.setEditable(false);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.setBackground(BG_GUTTER);
        g.setForeground(FG_GUTTER);
        g.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0x313244)),
                new EmptyBorder(0, 6, 0, 8)));
        g.setFocusable(false);
        return g;
    }

    private static void updateGutter(String xml, JTextArea gutter) {
        int n = 1;
        for (int i = 0; i < xml.length(); i++) if (xml.charAt(i) == '\n') n++;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) { if (i > 1) sb.append('\n'); sb.append(i); }
        gutter.setText(sb.toString());
    }

    // =========================================================================
    // Syntax highlighting
    // =========================================================================

    private void applyHighlighting(String xml, JTextPane target) {
        StyledDocument doc = new DefaultStyledDocument();
        try {
            int i = 0;
            while (i < xml.length()) {
                if (xml.startsWith("<?", i)) {
                    int end = xml.indexOf("?>", i) + 2;
                    if (end < 2) end = xml.length();
                    insertStyled(doc, xml.substring(i, end), C_DECL);
                    i = end;
                } else if (xml.startsWith("<!--", i)) {
                    int end = xml.indexOf("-->", i) + 3;
                    if (end < 3) end = xml.length();
                    insertStyled(doc, xml.substring(i, end), C_COMMENT);
                    i = end;
                } else if (xml.charAt(i) == '<') {
                    int end = xml.indexOf('>', i) + 1;
                    if (end <= 0) end = xml.length();
                    highlightTag(doc, xml.substring(i, end));
                    i = end;
                } else {
                    int end = xml.indexOf('<', i);
                    if (end < 0) end = xml.length();
                    insertStyled(doc, xml.substring(i, end), FG_DEFAULT);
                    i = end;
                }
            }
        } catch (Exception e) {
            try { doc.insertString(0, xml, null); } catch (BadLocationException ignored) {}
        }
        target.setDocument(doc);
    }

    private void highlightTag(StyledDocument doc, String tag) throws BadLocationException {
        int i = 0;
        insertStyled(doc, "<", C_TAG); i = 1;
        if (i < tag.length() && tag.charAt(i) == '/') { insertStyled(doc, "/", C_TAG); i++; }

        int nameEnd = i;
        while (nameEnd < tag.length() && tag.charAt(nameEnd) != ' ' && tag.charAt(nameEnd) != '>'
               && tag.charAt(nameEnd) != '/' && tag.charAt(nameEnd) != '\n') nameEnd++;
        insertStyled(doc, tag.substring(i, nameEnd), C_TAG);
        i = nameEnd;

        while (i < tag.length()) {
            char c = tag.charAt(i);
            if (c == '>' || (c == '/' && i + 1 < tag.length() && tag.charAt(i + 1) == '>')) {
                insertStyled(doc, tag.substring(i), C_TAG);
                break;
            }
            if (Character.isWhitespace(c) || c == '\n') {
                insertStyled(doc, String.valueOf(c), FG_DEFAULT); i++; continue;
            }
            int keyEnd = i;
            while (keyEnd < tag.length() && tag.charAt(keyEnd) != '='
                   && !Character.isWhitespace(tag.charAt(keyEnd)) && tag.charAt(keyEnd) != '>') keyEnd++;
            insertStyled(doc, tag.substring(i, keyEnd), C_ATTR_KEY);
            i = keyEnd;
            if (i < tag.length() && tag.charAt(i) == '=') { insertStyled(doc, "=", FG_DEFAULT); i++; }
            if (i < tag.length() && tag.charAt(i) == '"') {
                int valEnd = tag.indexOf('"', i + 1) + 1;
                if (valEnd <= 0) valEnd = tag.length();
                insertStyled(doc, tag.substring(i, valEnd), C_ATTR_VAL);
                i = valEnd;
            }
        }
    }

    private void insertStyled(StyledDocument doc, String text, Color color) throws BadLocationException {
        Style style = doc.addStyle(null, null);
        StyleConstants.setForeground(style, color);
        doc.insertString(doc.getLength(), text, style);
    }
}
