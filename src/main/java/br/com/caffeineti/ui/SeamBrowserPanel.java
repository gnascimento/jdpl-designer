package br.com.caffeineti.ui;

import br.com.caffeineti.seam.BeanScanner;
import br.com.caffeineti.seam.SeamBean;
import br.com.caffeineti.i18n.Messages;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bottom panel for browsing Seam/CDI beans detected in a selected project folder.
 * Supports:
 *  - selecting a project root folder (scans .class files and JARs)
 *  - tree view of beans grouped by type/scope
 *  - double-click to copy EL expression to clipboard
 */
public class SeamBrowserPanel extends JPanel {

    public interface BeanMethodListener {
        void onMethodSelected(SeamBean bean, String method);
    }

    public interface BeansListener {
        void onBeansChanged(List<SeamBean> beans);
    }

    private final BeanScanner scanner = new BeanScanner();
    private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(Messages.get("seam.root"));
    private final DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    private final JTree tree = new JTree(treeModel);
    private final JTextField searchField = new JTextField(18);
    private final JLabel statusLabel = new JLabel(Messages.get("seam.noProject"));
    private final Timer searchDebounceTimer = new Timer(250, e -> applyFilter());
    private BeanMethodListener beanMethodListener;
    private BeansListener beansListener;
    private File currentProjectDir;
    private List<SeamBean> currentBeans = List.of();

    public SeamBrowserPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xCFD8DC)));

        // ---- Toolbar ----
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(new Color(0xECEFF1));
        toolbar.setBorder(new EmptyBorder(2, 4, 2, 4));

        JLabel title = new JLabel(Messages.get("seam.title"));
        title.setFont(new Font("SansSerif", Font.BOLD, 11));
        title.setForeground(new Color(0x546E7A));
        toolbar.add(title);
        toolbar.addSeparator();

        JButton openBtn = new JButton(Messages.get("seam.openProject"));
        openBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        openBtn.setFocusPainted(false);
        openBtn.addActionListener(e -> chooseProject());
        toolbar.add(openBtn);

        JButton refreshBtn = new JButton(Messages.get("seam.refresh"));
        refreshBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        refreshBtn.setFocusPainted(false);
        refreshBtn.addActionListener(e -> { if (currentProjectDir != null) scanProject(currentProjectDir); });
        toolbar.add(refreshBtn);

        toolbar.addSeparator();
        JLabel searchLabel = new JLabel(Messages.get("seam.search"));
        searchLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        toolbar.add(searchLabel);
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 11));
        searchField.setMaximumSize(new Dimension(180, 24));
        TextFieldEditingSupport.install(searchField);
        searchDebounceTimer.setRepeats(false);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { searchDebounceTimer.restart(); }
            @Override public void removeUpdate(DocumentEvent e) { searchDebounceTimer.restart(); }
            @Override public void changedUpdate(DocumentEvent e) { searchDebounceTimer.restart(); }
        });
        toolbar.add(searchField);

        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(statusLabel);
        statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 10));
        statusLabel.setForeground(new Color(0x90A4AE));

        add(toolbar, BorderLayout.NORTH);

        // ---- Tree ----
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tree.setCellRenderer(new BeanTreeCellRenderer());

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path == null) return;
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    Object uo = node.getUserObject();
                    if (uo instanceof MethodEntry me) {
                        String expr = me.bean().getMethodExpression(me.method());
                        copyToClipboard(expr);
                        statusLabel.setText(Messages.get("seam.copied", expr));
                        if (beanMethodListener != null)
                            beanMethodListener.onMethodSelected(me.bean(), me.method());
                    }
                }
            }
        });

        // Tooltip
        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path != null) {
                    DefaultMutableTreeNode n = (DefaultMutableTreeNode) path.getLastPathComponent();
                    if (n.getUserObject() instanceof MethodEntry me)
                        tree.setToolTipText(Messages.get("seam.tooltipMethod", me.bean().getMethodExpression(me.method())));
                    else if (n.getUserObject() instanceof BeanEntry be)
                        tree.setToolTipText(be.bean().getClassName() + "  [" + be.bean().getScope() + "]");
                    else tree.setToolTipText(null);
                } else tree.setToolTipText(null);
            }
        });

        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    public void setBeanMethodListener(BeanMethodListener l) { this.beanMethodListener = l; }
    public void setBeansListener(BeansListener l) { this.beansListener = l; }
    public List<SeamBean> getCurrentBeans() { return List.copyOf(currentBeans); }

    private void chooseProject() {
        JFileChooser fc = new JFileChooser(currentProjectDir);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle(Messages.get("seam.selectProjectTitle"));
        int result = fc.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            currentProjectDir = fc.getSelectedFile();
            scanProject(currentProjectDir);
        }
    }

    private void scanProject(File dir) {
        statusLabel.setText(Messages.get("seam.scanning"));
        SwingWorker<List<SeamBean>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<SeamBean> doInBackground() {
                return scanner.scan(dir);
            }

            @Override
            protected void done() {
                try {
                    currentBeans = List.copyOf(get());
                    if (beansListener != null) beansListener.onBeansChanged(getCurrentBeans());
                    applyFilter();
                    if (searchField.getText().isBlank()) {
                        statusLabel.setText(Messages.get("seam.beansFound", currentBeans.size(), dir.getName()));
                    }
                } catch (Exception ex) {
                    statusLabel.setText(Messages.get("seam.error", ex.getMessage()));
                }
            }
        };
        worker.execute();
    }

    private void applyFilter() {
        List<SeamBean> filtered = filterBeans(searchField.getText());
        populateTree(filtered);
        if (!searchField.getText().isBlank()) {
            statusLabel.setText(Messages.get("seam.beansFiltered", filtered.size(), currentBeans.size()));
        } else if (currentProjectDir != null) {
            statusLabel.setText(Messages.get("seam.beansFound", currentBeans.size(), currentProjectDir.getName()));
        }
    }

    private List<SeamBean> filterBeans(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        if (q.isBlank()) return currentBeans;

        List<SeamBean> filtered = new ArrayList<>();
        for (SeamBean bean : currentBeans) {
            if (matches(bean, q)) filtered.add(bean);
        }
        return filtered;
    }

    private boolean matches(SeamBean bean, String query) {
        if (contains(bean.getBeanName(), query)
                || contains(bean.getClassName(), query)
                || contains(bean.getScope(), query)
                || contains(bean.getBeanType().name(), query)) {
            return true;
        }
        for (String method : bean.getMethods()) {
            if (contains(method, query) || contains(bean.getMethodExpression(method), query)) return true;
        }
        return false;
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void populateTree(List<SeamBean> beans) {
        rootNode.removeAllChildren();

        DefaultMutableTreeNode seamNode = new DefaultMutableTreeNode("JBoss Seam");
        DefaultMutableTreeNode cdiNode  = new DefaultMutableTreeNode("CDI");

        for (SeamBean bean : beans) {
            DefaultMutableTreeNode beanNode = new DefaultMutableTreeNode(new BeanEntry(bean));
            for (String method : bean.getMethods()) {
                beanNode.add(new DefaultMutableTreeNode(new MethodEntry(bean, method)));
            }
            if (bean.getBeanType() == SeamBean.BeanType.SEAM) seamNode.add(beanNode);
            else cdiNode.add(beanNode);
        }

        if (seamNode.getChildCount() > 0) rootNode.add(seamNode);
        if (cdiNode.getChildCount()  > 0) rootNode.add(cdiNode);

        treeModel.reload();
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    private void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
               .setContents(new StringSelection(text), null);
    }

    // -------------------------------------------------------------------------
    // Records for tree user objects
    // -------------------------------------------------------------------------

    private record BeanEntry(SeamBean bean) {
        @Override public String toString() {
            return bean.getBeanName() + "  (" + bean.getScope() + ")";
        }
    }

    private record MethodEntry(SeamBean bean, String method) {
        @Override public String toString() { return method + "()"; }
    }

    // -------------------------------------------------------------------------
    // Custom Cell Renderer
    // -------------------------------------------------------------------------

    private static class BeanTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            setFont(new Font("SansSerif", Font.PLAIN, 12));

            if (value instanceof DefaultMutableTreeNode node) {
                Object uo = node.getUserObject();
                if (uo instanceof BeanEntry be) {
                    setFont(new Font("SansSerif", Font.BOLD, 12));
                    Color c = be.bean().getBeanType() == SeamBean.BeanType.SEAM
                            ? new Color(0xB71C1C) : new Color(0x1565C0);
                    setForeground(sel ? Color.WHITE : c);
                } else if (uo instanceof MethodEntry) {
                    setForeground(sel ? Color.WHITE : new Color(0x37474F));
                    setText("  " + uo);
                } else if (uo instanceof String s) {
                    setFont(new Font("SansSerif", Font.BOLD, 11));
                    setForeground(sel ? Color.WHITE : new Color(0x546E7A));
                }
            }
            return this;
        }
    }
}
