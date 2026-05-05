package br.com.caffeineti.ui;

import br.com.caffeineti.model.*;
import br.com.caffeineti.i18n.Messages;
import br.com.caffeineti.xml.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Locale;

public class MainFrame extends JFrame {

    private ProcessDefinition process;

    private final DiagramCanvas    canvas;
    private final PalettePanel     palette;
    private final PropertiesPanel  properties;
    private final XmlViewPanel     xmlView;
    private final SeamBrowserPanel seamBrowser;

    private final JpdlSerializer   serializer = new JpdlSerializer();
    private final JpdlParser       parser     = new JpdlParser();

    private File currentFile;

    public MainFrame() {
        this(new ProcessDefinition(), null);
    }

    private MainFrame(ProcessDefinition process, File currentFile) {
        super(Messages.get("app.title"));
        this.process = process;
        this.currentFile = currentFile;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        // ---- Components ----
        canvas      = new DiagramCanvas(process);
        palette     = new PalettePanel(type -> {
            canvas.setPendingNodeType(type);
            canvas.requestFocusInWindow();
        });
        properties  = new PropertiesPanel();
        properties.setOwnerFrame(this);
        properties.setProcess(process);
        xmlView     = new XmlViewPanel(process);
        seamBrowser = new SeamBrowserPanel();

        // ---- Wire listeners ----
        canvas.addSelectionListener(new DiagramCanvas.SelectionListener() {
            @Override public void onNodeSelected(FlowNode node) {
                properties.showNode(node);
                palette.clearSelection();
            }
            @Override public void onTransitionSelected(Transition t) {
                properties.showTransition(t);
                palette.clearSelection();
            }
            @Override public void onSelectionCleared() {
                properties.showEmpty();
                palette.clearSelection();
            }
            @Override public void onProcessChanged() {
                xmlView.refresh();
                updateTitle();
            }
        });

        properties.setChangeListener(() -> {
            xmlView.refresh();
            updateTitle();
            // Repaint the canvas after all event processing settles so swimlane
            // bands (which are derived from node state) are always up to date.
            SwingUtilities.invokeLater(canvas::repaint);
        });

        seamBrowser.setBeanMethodListener((bean, method) -> {
            // Try to inject the EL expression into the focused properties field
            properties.injectBeanMethod(bean, method);
        });
        seamBrowser.setBeansListener(properties::setSeamBeans);

        // ---- Layout ----
        setJMenuBar(buildMenuBar());

        JPanel toolbar = buildToolBar();

        // Left: palette
        // Center: canvas in scroll pane
        JScrollPane canvasScroll = new JScrollPane(canvas);
        canvasScroll.getViewport().setBackground(new Color(0xF5F5F5));
        canvasScroll.setBorder(BorderFactory.createEmptyBorder());

        // "Designer" tab = palette + canvas
        JPanel designerTab = new JPanel(new BorderLayout());
        designerTab.add(palette, BorderLayout.WEST);
        designerTab.add(canvasScroll, BorderLayout.CENTER);

        // Top tabbed pane: Designer | XML Preview
        JTabbedPane mainTabs = new JTabbedPane(JTabbedPane.TOP);
        mainTabs.setFont(new Font("SansSerif", Font.PLAIN, 12));
        mainTabs.addTab(Messages.get("tab.designer"),   designerTab);
        mainTabs.addTab(Messages.get("tab.xmlPreview"), xmlView);

        // Top area = main tabs (left) + properties (right)
        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mainTabs, properties);
        topSplit.setResizeWeight(1.0);
        topSplit.setDividerSize(6);
        topSplit.setBorder(null);
        topSplit.setContinuousLayout(true);
        topSplit.setDividerLocation(getWidth() - properties.getPreferredSize().width);

        // Full layout = top split (above) + Seam/CDI browser (below)
        JSplitPane vertSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, seamBrowser);
        vertSplit.setResizeWeight(0.75);
        vertSplit.setDividerSize(5);
        vertSplit.setBorder(null);

        add(toolbar, BorderLayout.NORTH);
        add(vertSplit, BorderLayout.CENTER);

        // Initial XML render
        xmlView.refresh();
        updateTitle();
    }

    // =========================================================================
    // Menu Bar
    // =========================================================================

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();

        // File
        JMenu fileMenu = new JMenu(Messages.get("menu.file"));
        addMenuItem(fileMenu, Messages.get("menu.file.new"),    "Ctrl+N", this::newProcess);
        addMenuItem(fileMenu, Messages.get("menu.file.open"),   "Ctrl+O", this::openFile);
        fileMenu.addSeparator();
        addMenuItem(fileMenu, Messages.get("menu.file.save"),   "Ctrl+S", this::saveFile);
        addMenuItem(fileMenu, Messages.get("menu.file.saveAs"), null,     this::saveFileAs);
        fileMenu.addSeparator();
        addMenuItem(fileMenu, Messages.get("menu.file.exit"),   "Alt+F4", e -> System.exit(0));
        mb.add(fileMenu);

        // Edit
        JMenu editMenu = new JMenu(Messages.get("menu.edit"));
        addMenuItem(editMenu, Messages.get("menu.edit.deleteSelected"), "DELETE", e -> {
            FlowNode n = canvas.getSelectedNode();
            Transition t = canvas.getSelectedTransition();
            if (n != null) { process.removeNode(n); properties.showEmpty(); }
            else if (t != null) { process.removeTransition(t); properties.showEmpty(); }
            canvas.repaint();
            xmlView.refresh();
            updateTitle();
        });
        mb.add(editMenu);

        // Process
        JMenu processMenu = new JMenu(Messages.get("menu.process"));
        addMenuItem(processMenu, Messages.get("menu.process.setName"),   null,           this::setProcessName);
        addMenuItem(processMenu, Messages.get("menu.process.swimlanes"), "Ctrl+Shift+W", this::manageSwimlanes);
        processMenu.addSeparator();
        addMenuItem(processMenu, Messages.get("menu.process.fit"),        "Ctrl+Shift+F", e -> canvas.fitToWindow());
        addMenuItem(processMenu, Messages.get("menu.process.autoLayout"), "Ctrl+Shift+L", e -> canvas.autoLayout());
        mb.add(processMenu);

        JMenu languageMenu = new JMenu(Messages.get("menu.language"));
        ButtonGroup languageGroup = new ButtonGroup();
        addLanguageItem(languageMenu, languageGroup, Messages.get("menu.language.english"), Messages.ENGLISH);
        addLanguageItem(languageMenu, languageGroup, Messages.get("menu.language.portugueseBrazil"), Messages.PORTUGUESE_BRAZIL);
        mb.add(languageMenu);

        // Help
        JMenu helpMenu = new JMenu(Messages.get("menu.help"));
        addMenuItem(helpMenu, Messages.get("menu.help.about"), null, e -> JOptionPane.showMessageDialog(this,
                Messages.get("about.message"),
                Messages.get("about.title"), JOptionPane.INFORMATION_MESSAGE));
        mb.add(helpMenu);

        return mb;
    }

    private void addLanguageItem(JMenu menu, ButtonGroup group, String text, Locale locale) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(text);
        item.setSelected(Messages.getLocale().equals(locale));
        item.addActionListener(e -> switchLanguage(locale));
        group.add(item);
        menu.add(item);
    }

    private void switchLanguage(Locale locale) {
        if (Messages.getLocale().equals(locale)) return;
        Messages.setLocale(locale);
        MainFrame frame = new MainFrame(process, currentFile);
        frame.setVisible(true);
        dispose();
    }

    private void addMenuItem(JMenu menu, String text, String accel, ActionListener al) {
        JMenuItem mi = new JMenuItem(text);
        if (accel != null) mi.setAccelerator(KeyStroke.getKeyStroke(accel));
        mi.addActionListener(al);
        menu.add(mi);
    }

    // =========================================================================
    // Toolbar
    // =========================================================================

    private JPanel buildToolBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        bar.setBackground(new Color(0xECEFF1));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xCFD8DC)));

        bar.add(toolBtn(Messages.get("toolbar.new"),  "Ctrl+N", this::newProcess));
        bar.add(toolBtn(Messages.get("toolbar.open"), "Ctrl+O", this::openFile));
        bar.add(toolBtn(Messages.get("toolbar.save"), "Ctrl+S", this::saveFile));
        bar.add(new JSeparator(SwingConstants.VERTICAL));
        bar.add(toolBtn(Messages.get("toolbar.fit"),        null, e -> canvas.fitToWindow()));
        bar.add(toolBtn(Messages.get("toolbar.autoLayout"), null, e -> canvas.autoLayout()));
        bar.add(toolBtn(Messages.get("toolbar.swimlanes"),  null, this::manageSwimlanes));
        bar.add(new JSeparator(SwingConstants.VERTICAL));

        JLabel zoomLabel = new JLabel(Messages.get("toolbar.zoomHint"));
        zoomLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        zoomLabel.setForeground(new Color(0x90A4AE));
        bar.add(zoomLabel);

        return bar;
    }

    private JButton toolBtn(String text, String accel, ActionListener al) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btn.setFocusPainted(false);
        if (accel != null) btn.setMnemonic(text.charAt(0));
        btn.addActionListener(al);
        return btn;
    }

    // =========================================================================
    // Actions
    // =========================================================================

    private void newProcess(ActionEvent e) {
        if (confirmDiscard()) {
            process.clear();
            process.setName("NewProcess");
            canvas.setProcess(process);
            xmlView.setProcess(process);
            properties.setProcess(process);
            currentFile = null;
            properties.showEmpty();
            updateTitle();
        }
    }

    private void openFile(ActionEvent e) {
        if (!confirmDiscard()) return;
        JFileChooser fc = new JFileChooser(currentFile);
        fc.setFileFilter(new FileNameExtensionFilter(Messages.get("fileFilter.jpdlXml"), "jpdl", "xml"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                ProcessDefinition loaded = parser.parse(fc.getSelectedFile());
                this.process = loaded;
                canvas.setProcess(process);
                xmlView.setProcess(process);
                properties.setProcess(process);
                currentFile = fc.getSelectedFile();
                properties.showEmpty();
                canvas.fitToWindow();
                updateTitle();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, Messages.get("dialog.open.error", ex.getMessage()),
                        Messages.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveFile(ActionEvent e) {
        if (currentFile == null) { saveFileAs(e); return; }
        doSave(currentFile);
    }

    private void saveFileAs(ActionEvent e) {
        JFileChooser fc = new JFileChooser(currentFile);
        FileNameExtensionFilter xmlFilter = new FileNameExtensionFilter(Messages.get("fileFilter.xml"), "xml");
        FileNameExtensionFilter jpdlFilter = new FileNameExtensionFilter(Messages.get("fileFilter.jpdl"), "jpdl");
        fc.addChoosableFileFilter(xmlFilter);
        fc.addChoosableFileFilter(jpdlFilter);
        fc.setFileFilter(xmlFilter);
        fc.setSelectedFile(new File(process.getName().replace(' ', '_') + ".xml"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (!f.getName().contains(".")) {
                String ext = fc.getFileFilter() == jpdlFilter ? ".jpdl" : ".xml";
                f = new File(f.getPath() + ext);
            }
            currentFile = f;
            doSave(currentFile);
        }
    }

    private void doSave(File file) {
        try {
            serializer.serialize(process, file);
            updateTitle();
            JOptionPane.showMessageDialog(this, Messages.get("dialog.save.success", file.getAbsolutePath()),
                    Messages.get("dialog.saved.title"), JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, Messages.get("dialog.save.error", ex.getMessage()),
                    Messages.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void manageSwimlanes(ActionEvent e) {
        SwimlanesDialog dlg = new SwimlanesDialog(this, process);
        dlg.setVisible(true);
        canvas.repaint(); // refresh bands after dialog closes
    }

    private void setProcessName(ActionEvent e) {
        String name = JOptionPane.showInputDialog(this, Messages.get("dialog.processName"), process.getName());
        if (name != null && !name.isBlank()) {
            process.setName(name.trim());
            xmlView.refresh();
            updateTitle();
        }
    }

    private boolean confirmDiscard() {
        int r = JOptionPane.showConfirmDialog(this,
                Messages.get("dialog.discard"), Messages.get("dialog.confirm.title"), JOptionPane.YES_NO_OPTION);
        return r == JOptionPane.YES_OPTION;
    }

    private void updateTitle() {
        String dirty = "";
        String file = currentFile != null ? " — " + currentFile.getName() : "";
        setTitle(Messages.get("app.title") + file + " [" + process.getName() + "]" + dirty);
    }
}
