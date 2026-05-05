package br.com.caffeineti.ui;

import br.com.caffeineti.seam.SeamBean;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

final class ElExpressionAutoComplete {
    private static final int MAX_SUGGESTIONS = 8;

    private ElExpressionAutoComplete() {}

    static void install(JTextField field, Supplier<List<SeamBean>> beanSupplier) {
        TextFieldEditingSupport.install(field);
        CompletionPopup completion = new CompletionPopup(field, beanSupplier);
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { completion.refreshLater(); }
            @Override public void removeUpdate(DocumentEvent e) { completion.refreshLater(); }
            @Override public void changedUpdate(DocumentEvent e) { completion.refreshLater(); }
        });
        field.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { completion.handleKey(e); }
        });
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { completion.refreshLater(); }
            @Override public void focusLost(FocusEvent e) { SwingUtilities.invokeLater(completion::hide); }
        });
    }

    private static final class CompletionPopup {
        private final JTextField field;
        private final Supplier<List<SeamBean>> beanSupplier;
        private final JPopupMenu popup = new JPopupMenu();
        private final DefaultListModel<Completion> model = new DefaultListModel<>();
        private final JList<Completion> list = new JList<>(model);
        private boolean applying;

        CompletionPopup(JTextField field, Supplier<List<SeamBean>> beanSupplier) {
            this.field = field;
            this.beanSupplier = beanSupplier;
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setVisibleRowCount(MAX_SUGGESTIONS);
            list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            list.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) applySelection();
                }
            });
            popup.setFocusable(false);
            popup.add(new JScrollPane(list));
        }

        void refreshLater() {
            if (applying) return;
            SwingUtilities.invokeLater(this::refresh);
        }

        void refresh() {
            if (!field.isShowing() || !field.isEditable() || !field.hasFocus()) {
                hide();
                return;
            }

            CompletionContext context = CompletionContext.from(field);
            List<Completion> completions = findCompletions(context.query());
            model.clear();
            completions.forEach(model::addElement);
            if (model.isEmpty()) {
                hide();
                return;
            }

            list.setSelectedIndex(0);
            list.setFixedCellHeight(24);
            int width = Math.max(field.getWidth(), 360);
            int height = Math.min(model.size(), MAX_SUGGESTIONS) * list.getFixedCellHeight() + 4;
            popup.setPopupSize(width, height);
            if (!popup.isVisible()) popup.show(field, 0, field.getHeight());
        }

        void handleKey(KeyEvent e) {
            if (!popup.isVisible()) return;
            switch (e.getKeyCode()) {
                case KeyEvent.VK_DOWN -> {
                    int next = Math.min(list.getSelectedIndex() + 1, model.size() - 1);
                    list.setSelectedIndex(next);
                    list.ensureIndexIsVisible(next);
                    e.consume();
                }
                case KeyEvent.VK_UP -> {
                    int prev = Math.max(list.getSelectedIndex() - 1, 0);
                    list.setSelectedIndex(prev);
                    list.ensureIndexIsVisible(prev);
                    e.consume();
                }
                case KeyEvent.VK_ENTER, KeyEvent.VK_TAB -> {
                    applySelection();
                    e.consume();
                }
                case KeyEvent.VK_ESCAPE -> {
                    hide();
                    e.consume();
                }
                default -> {}
            }
        }

        void hide() {
            popup.setVisible(false);
        }

        private void applySelection() {
            Completion selected = list.getSelectedValue();
            if (selected == null) return;
            CompletionContext context = CompletionContext.from(field);
            applying = true;
            String text = field.getText();
            String nextText = text.substring(0, context.start())
                    + selected.expression()
                    + text.substring(context.end());
            int caret = context.start() + selected.expression().length();
            field.setText(nextText);
            field.setCaretPosition(caret);
            applying = false;
            hide();
            field.postActionEvent();
        }

        private List<Completion> findCompletions(String currentText) {
            String query = normalize(currentText);
            List<Completion> result = new ArrayList<>();
            for (SeamBean bean : beanSupplier.get()) {
                for (String method : bean.getMethods()) {
                    Completion completion = new Completion(bean, method);
                    if (query.isBlank() || completion.matches(query)) {
                        result.add(completion);
                        if (result.size() >= MAX_SUGGESTIONS) return result;
                    }
                }
            }
            return result;
        }

        private static String normalize(String value) {
            if (value == null) return "";
            return value
                    .replace("#{", "")
                    .replace("}", "")
                    .toLowerCase(Locale.ROOT)
                    .trim();
        }
    }

    private record CompletionContext(int start, int end, String query) {
        static CompletionContext from(JTextField field) {
            String text = field.getText();
            int caret = Math.max(0, Math.min(field.getCaretPosition(), text.length()));
            int selectionStart = field.getSelectionStart();
            int selectionEnd = field.getSelectionEnd();
            if (selectionStart != selectionEnd) {
                String selected = text.substring(selectionStart, selectionEnd);
                return new CompletionContext(selectionStart, selectionEnd, selected);
            }

            int open = text.lastIndexOf("#{", Math.max(0, caret - 1));
            int closeBefore = text.lastIndexOf('}', Math.max(0, caret - 1));
            if (open >= 0 && open > closeBefore && caret >= open + 2) {
                int end = text.indexOf('}', caret);
                if (end < 0) end = caret;
                else end++;
                return new CompletionContext(open, end, text.substring(open + 2, caret));
            }

            int start = caret;
            while (start > 0 && isTokenChar(text.charAt(start - 1))) start--;
            int end = caret;
            while (end < text.length() && isTokenChar(text.charAt(end))) end++;
            return new CompletionContext(start, end, text.substring(start, caret));
        }

        private static boolean isTokenChar(char c) {
            return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.';
        }
    }

    private record Completion(SeamBean bean, String method) {
        String expression() {
            return bean.getMethodExpression(method);
        }

        boolean matches(String query) {
            return normalize(bean.getBeanName()).contains(query)
                    || normalize(method).contains(query)
                    || normalize(bean.getClassName()).contains(query)
                    || normalize(expression()).contains(query);
        }

        @Override public String toString() {
            return expression() + "  -  " + bean.getClassName();
        }

        private static String normalize(String value) {
            return value == null ? "" : value.toLowerCase(Locale.ROOT);
        }
    }
}
