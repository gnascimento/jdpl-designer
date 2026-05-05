package br.com.caffeineti.ui;

import javax.swing.*;
import javax.swing.text.DefaultEditorKit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

final class TextFieldEditingSupport {
    private TextFieldEditingSupport() {}

    static void install(JTextField field) {
        bind(field, KeyEvent.VK_C, DefaultEditorKit.copyAction);
        bind(field, KeyEvent.VK_X, DefaultEditorKit.cutAction);
        bind(field, KeyEvent.VK_V, DefaultEditorKit.pasteAction);
        bind(field, KeyEvent.VK_A, DefaultEditorKit.selectAllAction);
    }

    private static void bind(JTextField field, int keyCode, String actionName) {
        InputMap inputMap = field.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = field.getActionMap();
        Object action = actionMap.get(actionName);
        if (action == null) return;

        String ctrlKey = "ctrl-" + actionName;
        inputMap.put(KeyStroke.getKeyStroke(keyCode, InputEvent.CTRL_DOWN_MASK), ctrlKey);
        actionMap.put(ctrlKey, (Action) action);

        String metaKey = "meta-" + actionName;
        inputMap.put(KeyStroke.getKeyStroke(keyCode, InputEvent.META_DOWN_MASK), metaKey);
        actionMap.put(metaKey, (Action) action);
    }
}
