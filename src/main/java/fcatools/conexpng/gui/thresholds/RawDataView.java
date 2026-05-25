package fcatools.conexpng.gui.thresholds;

import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import javax.swing.event.*;

import fcatools.conexpng.Conf;
import fcatools.conexpng.Conf.ContextChangeEvent;
import fcatools.conexpng.ContextChangeEvents;
import fcatools.conexpng.gui.View;
import fcatools.conexpng.gui.lattice.LatticeGraph;
import fcatools.conexpng.gui.thresholds.RawDataConfig.Mode;

/**
 * Raw Data View – Conversion Continu → Booléen
 *
 * Phase 1: Manual threshold and Min/Max interval modes.
 * Phase 2+: Natural thresholds and Renard series.
 */
@SuppressWarnings("serial")
public class RawDataView extends View {

    private static final Color COLOR_HEADER   = new Color(220, 220, 220);
    private static final Color COLOR_ABOVE    = new Color(200, 240, 200);  // Green → will be checked
    private static final Color COLOR_BELOW    = new Color(255, 220, 220);  // Red → will be empty
    private static final Color COLOR_EMPTY    = new Color(250, 250, 250);
    private static final Color COLOR_DISABLED = new Color(235, 235, 235);
    private static final Color COLOR_APPLY_BG = new Color(60, 120, 180);

    private Map<String, RawDataConfig> rawDataConfigs = new LinkedHashMap<>();
    private Map<String, Map<String, Double>> continuousValues = new LinkedHashMap<>();

    private JTable      configTable;
    private JTable      matrixTable;
    private ConfigTableModel configModel;
    private MatrixTableModel matrixModel;

    public RawDataView(Conf state) {
        super(state);
        setLayout(new BorderLayout());
        buildUI();
        syncFromContext();
    }

    private void buildUI() {
        configModel = new ConfigTableModel();
        configTable = new JTable(configModel);
        configTable.setRowHeight(80);
        configTable.setFont(configTable.getFont().deriveFont(13f));
        configTable.getTableHeader().setReorderingAllowed(false);
        configTable.getTableHeader().setFont(configTable.getFont().deriveFont(Font.BOLD));
        
        configTable.setDefaultRenderer(Object.class, new ConfigCellRenderer());
        configTable.getColumnModel().getColumn(1).setCellRenderer(new ModeRenderer());
        configTable.getColumnModel().getColumn(1).setCellEditor(new ModeEditor());
        
        configTable.setGridColor(new Color(160, 160, 160));
        configTable.setShowGrid(true);
        configTable.setIntercellSpacing(new Dimension(1, 1));

        JScrollPane configScroll = new JScrollPane(configTable);
        configScroll.setPreferredSize(new Dimension(0, 200));
        configScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "  Raw Data Configuration  —  Select mode and define values  ",
                TitledBorder.LEFT, TitledBorder.TOP,
                configTable.getFont().deriveFont(Font.BOLD)));

        // ✅ Buttons for adding/removing attributes and objects
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        
        JButton addAttrButton = new JButton("+ Add Attribute");
        addAttrButton.setFont(addAttrButton.getFont().deriveFont(Font.PLAIN, 11f));
        addAttrButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addAttribute();
            }
        });
        
        JButton removeAttrButton = new JButton("- Remove Attribute");
        removeAttrButton.setFont(removeAttrButton.getFont().deriveFont(Font.PLAIN, 11f));
        removeAttrButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                removeAttribute();
            }
        });
        
        JButton addObjButton = new JButton("+ Add Object");
        addObjButton.setFont(addObjButton.getFont().deriveFont(Font.PLAIN, 11f));
        addObjButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addObject();
            }
        });
        
        JButton removeObjButton = new JButton("- Remove Object");
        removeObjButton.setFont(removeObjButton.getFont().deriveFont(Font.PLAIN, 11f));
        removeObjButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                removeObject();
            }
        });
        
        buttonPanel.add(addAttrButton);
        buttonPanel.add(removeAttrButton);
        buttonPanel.add(new JLabel("  |  "));  // Separator
        buttonPanel.add(addObjButton);
        buttonPanel.add(removeObjButton);
        
        JPanel configPanel = new JPanel(new BorderLayout());
        configPanel.add(configScroll, BorderLayout.CENTER);
        configPanel.add(buttonPanel, BorderLayout.SOUTH);

        matrixModel = new MatrixTableModel();
        matrixTable = new JTable(matrixModel);
        matrixTable.setRowHeight(26);
        matrixTable.setFont(matrixTable.getFont().deriveFont(13f));
        matrixTable.getTableHeader().setReorderingAllowed(false);
        matrixTable.getTableHeader().setFont(matrixTable.getFont().deriveFont(Font.BOLD));
        matrixTable.setDefaultRenderer(Object.class, new MatrixCellRenderer());
        matrixTable.setDefaultEditor(Object.class, new DoubleCellEditor());
        matrixTable.setGridColor(new Color(160, 160, 160));
        matrixTable.setShowGrid(true);
        matrixTable.setIntercellSpacing(new Dimension(1, 1));
        
        // ✅ Enable Excel-like selection: drag to select cells/rows/columns
        matrixTable.setRowSelectionAllowed(true);
        matrixTable.setColumnSelectionAllowed(true);
        matrixTable.setCellSelectionEnabled(true);
        matrixTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        // ✅ Click on column header to select entire column (like Excel)
        matrixTable.getTableHeader().addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int col = matrixTable.columnAtPoint(e.getPoint());
                if (col >= 0) {
                    matrixTable.setColumnSelectionInterval(col, col);
                    matrixTable.setRowSelectionInterval(0, matrixTable.getRowCount() - 1);
                }
            }
        });
        
        // ✅ Enable copy/paste with Ctrl+C / Ctrl+V (using InputMap/ActionMap)
        InputMap im = matrixTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = matrixTable.getActionMap();
        
        // Ctrl+C for copy
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
        am.put("copy", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                copySelection();
            }
        });
        
        // Ctrl+V for paste
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste");
        am.put("paste", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                pasteSelection();
            }
        });

        JScrollPane matrixScroll = new JScrollPane(matrixTable);
        matrixScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "  Continuous Values  —  enter a numeric value for each object × attribute  "
                + "  |  green = will be ✓  |  red = will be empty  ",
                TitledBorder.LEFT, TitledBorder.TOP,
                matrixTable.getFont().deriveFont(Font.BOLD)));

        JButton applyButton = new JButton("Apply to Context Editor & Lattice");
        applyButton.setFont(applyButton.getFont().deriveFont(Font.BOLD, 13f));
        applyButton.setBackground(COLOR_APPLY_BG);
        applyButton.setForeground(Color.BLACK);  // ✅ BLACK text instead of white
        applyButton.setOpaque(true);
        applyButton.setBorderPainted(false);
        applyButton.setPreferredSize(new Dimension(280, 36));
        applyButton.setFocusPainted(false);
        applyButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                applyRawData();
            }
        });

        // ✅ NEW: Import Excel button
        JButton importButton = new JButton("Import Excel...");
        importButton.setFont(importButton.getFont().deriveFont(Font.BOLD, 13f));
        importButton.setBackground(new Color(70, 140, 70));
        importButton.setForeground(Color.BLACK);
        importButton.setOpaque(true);
        importButton.setBorderPainted(false);
        importButton.setPreferredSize(new Dimension(150, 36));
        importButton.setFocusPainted(false);
        importButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                importExcelFile();
            }
        });

        JLabel hintLabel = new JLabel(
                "  After applying, switch to Lattice tab to generate the updated lattice.");
        hintLabel.setForeground(new Color(90, 90, 90));
        hintLabel.setFont(hintLabel.getFont().deriveFont(12f));

        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        southPanel.setBorder(BorderFactory.createMatteBorder(
                1, 0, 0, 0, new Color(140, 140, 140)));
        southPanel.add(importButton);  // ✅ Import Excel button
        southPanel.add(applyButton);
        southPanel.add(hintLabel);

        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, configPanel, matrixScroll);
        split.setResizeWeight(0.35);
        split.setDividerSize(6);
        split.setBorder(new EmptyBorder(4, 4, 4, 4));

        add(split,      BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
    }

    private void syncFromContext() {
        List<String> attrs = getAttributeList();
        List<String> objs  = getObjectList();

        Map<String, RawDataConfig> newConfigs = new LinkedHashMap<>();
        for (String attr : attrs) {
            newConfigs.put(attr,
                    rawDataConfigs.containsKey(attr)
                    ? rawDataConfigs.get(attr)
                    : new RawDataConfig(attr));
        }
        rawDataConfigs = newConfigs;

        Map<String, Map<String, Double>> newValues = new LinkedHashMap<>();
        for (String obj : objs) {
            newValues.put(obj,
                    continuousValues.containsKey(obj)
                    ? continuousValues.get(obj)
                    : new LinkedHashMap<String, Double>());
        }
        continuousValues = newValues;

        configModel.refresh();
        matrixModel.refresh();
    }

    private List<String> getAttributeList() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < state.context.getAttributeCount(); i++) {
            result.add(state.context.getAttributeAtIndex(i));
        }
        return result;
    }

    private List<String> getObjectList() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < state.context.getObjectCount(); i++) {
            result.add(state.context.getObjectAtIndex(i).getIdentifier());
        }
        return result;
    }

    private void applyRawData() {
        if (configTable.isEditing()) configTable.getCellEditor().stopCellEditing();
        if (matrixTable.isEditing()) matrixTable.getCellEditor().stopCellEditing();

        List<String> attrs = getAttributeList();
        List<String> objs  = getObjectList();

        if (attrs.isEmpty() || objs.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "The context is empty. Please define attributes and objects first\n"
                    + "in the Context Editor tab.",
                    "Nothing to apply", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int applied = 0;
        int skipped = 0;

        for (String obj : objs) {
            Map<String, Double> objValues = continuousValues.get(obj);

            for (String attr : attrs) {
                RawDataConfig cfg   = rawDataConfigs.get(attr);
                Double        value = (objValues != null) ? objValues.get(attr) : null;

                if (value == null || cfg == null) {
                    skipped++;
                    continue;
                }

                // ✅ Use shouldBeChecked() based on mode
                boolean shouldHaveAttr = cfg.shouldBeChecked(value);
                boolean hasAttrNow     = state.context.objectHasAttribute(
                        state.context.getObject(obj), attr);

                if (shouldHaveAttr != hasAttrNow) {
                    state.context.toggleAttributeForObject(attr, obj);
                }
                applied++;
            }
        }

        state.lattice   = new LatticeGraph();
        state.concepts  = new java.util.HashSet<>();
        state.contextChanged();

        String msg = "Done! " + applied + " cell(s) processed.";
        if (skipped > 0) {
            msg += "\n" + skipped + " cell(s) skipped (no value entered — left unchanged).";
        }
        msg += "\n\nYou can verify the result in the Context Editor tab,\n"
             + "then generate the lattice in the Lattice tab.";

        JOptionPane.showMessageDialog(this, msg,
                "Raw Data Applied", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Import continuous values from an Excel file (.xlsx or .xls).
     */
    private void importExcelFile() {
        // ── Step 1: Choose file ──────────────────────────────────────────────
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Import Excel File");
        fileChooser.setFileFilter(new FileNameExtensionFilter(
                "Excel Files (.xlsx, .xls)", "xlsx", "xls"));
        
        int result = fileChooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;  // User cancelled
        }
        
        File file = fileChooser.getSelectedFile();
        
        // ── Step 2: Import data ───────────────────────────────────────────────
        ExcelData excelData;
        try {
            excelData = ExcelImporter.importFile(file);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to import Excel file:\n\n" + ex.getMessage(),
                    "Import Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // ── Step 3: Show reconciliation dialog ────────────────────────────────
        List<String> newAttributes = new ArrayList<String>();
        List<String> newObjects = new ArrayList<String>();
        
        boolean confirmed = ImportReconciliationDialog.showDialog(
                (JFrame) SwingUtilities.getWindowAncestor(this),
                excelData, state, newAttributes, newObjects);
        
        if (!confirmed) {
            return;  // User cancelled
        }
        
        // ── Step 4: Add new attributes/objects to context ─────────────────────
        for (String attr : newAttributes) {
            state.context.addAttribute(attr);
        }
        
        try {
            for (String obj : newObjects) {
                state.context.addObject(new de.tudresden.inf.tcs.fcalib.FullObject(obj));
            }
        } catch (de.tudresden.inf.tcs.fcaapi.exception.IllegalObjectException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to add objects to context:\n\n" + e.getMessage(),
                    "Import Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // ── Step 5: Sync and populate values ───────────────────────────────────
        syncFromContext();  // Refresh tables
        
        // Populate continuous values from Excel
        for (String obj : excelData.objects) {
            Map<String, Double> objValues = continuousValues.get(obj);
            if (objValues == null) {
                objValues = new LinkedHashMap<String, Double>();
                continuousValues.put(obj, objValues);
            }
            
            Map<String, Double> excelObjValues = excelData.values.get(obj);
            if (excelObjValues != null) {
                objValues.putAll(excelObjValues);
            }
        }
        
        // Refresh matrix display
        matrixModel.refresh();
        
        // ── Step 6: Success message ───────────────────────────────────────────
        state.contextChanged();
        
        JOptionPane.showMessageDialog(this,
                "Import successful!\n\n" +
                "Attributes: " + excelData.attributes.size() + 
                " (" + newAttributes.size() + " new)\n" +
                "Objects: " + excelData.objects.size() + 
                " (" + newObjects.size() + " new)\n" +
                "Values loaded: " + excelData.validCells + "\n\n" +
                "You can now adjust thresholds and click Apply.",
                "Import Complete",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Copy selected cells to clipboard (Ctrl+C).
     */
    private void copySelection() {
        int[] selectedRows = matrixTable.getSelectedRows();
        int[] selectedCols = matrixTable.getSelectedColumns();
        
        if (selectedRows.length == 0 || selectedCols.length == 0) {
            return;  // Nothing selected
        }
        
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < selectedRows.length; i++) {
            for (int j = 0; j < selectedCols.length; j++) {
                int row = selectedRows[i];
                int col = selectedCols[j];
                
                Object value = matrixTable.getValueAt(row, col);
                sb.append(value != null ? value.toString() : "");
                
                if (j < selectedCols.length - 1) {
                    sb.append("\t");  // Tab between columns
                }
            }
            if (i < selectedRows.length - 1) {
                sb.append("\n");  // Newline between rows
            }
        }
        
        // Copy to system clipboard
        StringSelection selection = new StringSelection(sb.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
    }

    /**
     * Paste clipboard content to selected cells (Ctrl+V).
     */
    private void pasteSelection() {
        int startRow = matrixTable.getSelectedRow();
        int startCol = matrixTable.getSelectedColumn();
        
        if (startRow < 0 || startCol < 0) {
            return;  // No cell selected
        }
        
        try {
            // Get clipboard content
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            String data = (String) clipboard.getData(DataFlavor.stringFlavor);
            
            if (data == null || data.isEmpty()) {
                return;
            }
            
            // Parse clipboard data (tab-separated columns, newline-separated rows)
            String[] rows = data.split("\n");
            int clipboardRows = rows.length;
            int clipboardCols = rows.length > 0 ? rows[0].split("\t").length : 0;
            
            // ✅ Check if selection size matches clipboard size (like Excel)
            int[] selectedRows = matrixTable.getSelectedRows();
            int[] selectedCols = matrixTable.getSelectedColumns();
            
            if (selectedRows.length > 1 || selectedCols.length > 1) {
                // Multiple cells selected - check size match
                if (selectedRows.length != clipboardRows || selectedCols.length != clipboardCols) {
                    // Size mismatch - show Excel-like warning
                    int result = JOptionPane.showConfirmDialog(
                            this,
                            "La taille des données que vous collez ne correspond pas\n" +
                            "à votre sélection. Voulez-vous quand même les coller ?",
                            "Avertissement",
                            JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.WARNING_MESSAGE);
                    
                    if (result != JOptionPane.OK_OPTION) {
                        return;  // User cancelled
                    }
                }
            }
            
            // Continue with paste operation
            for (int i = 0; i < rows.length; i++) {
                int targetRow = startRow + i;
                if (targetRow >= matrixTable.getRowCount()) {
                    break;  // Stop if exceeds table rows
                }
                
                String[] cols = rows[i].split("\t");
                
                for (int j = 0; j < cols.length; j++) {
                    int targetCol = startCol + j;
                    if (targetCol >= matrixTable.getColumnCount()) {
                        break;  // Stop if exceeds table columns
                    }
                    
                    if (targetCol == 0) {
                        continue;  // Skip first column (object names, read-only)
                    }
                    
                    // Set value (will be validated by model)
                    matrixTable.setValueAt(cols[j].trim(), targetRow, targetCol);
                }
            }
            
            matrixTable.repaint();
            
        } catch (Exception ex) {
            // Clipboard error - silently ignore
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if (e instanceof ContextChangeEvent) {
            ContextChangeEvent cce = (ContextChangeEvent) e;
            if (cce.getName() == ContextChangeEvents.CONTEXTCHANGED
             || cce.getName() == ContextChangeEvents.NEWCONTEXT
             || cce.getName() == ContextChangeEvents.LOADEDFILE) {
                syncFromContext();
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Config Table Model: Attribute | Modalité | Min | Max | Threshold
    // ═════════════════════════════════════════════════════════════════════════

    private class ConfigTableModel extends AbstractTableModel {

        private final String[] COLS = { "Attribute", "Modalité", "Min", "Max", "Threshold" };
        
        private List<String> attrList = new ArrayList<>();

        void refresh() {
            attrList = new ArrayList<>(rawDataConfigs.keySet());
            fireTableDataChanged();
        }

        @Override public int    getRowCount()              { return attrList.size(); }
        @Override public int    getColumnCount()           { return COLS.length; }
        @Override public String getColumnName(int col)     { return COLS[col]; }
        
        @Override
        public boolean isCellEditable(int row, int col) {
            if (col == 0) return false;
            if (col == 1) return true;
            
            String attr = attrList.get(row);
            RawDataConfig cfg = rawDataConfigs.get(attr);
            if (cfg == null) return false;
            
            if (col == 2) return cfg.isMinEditable();
            if (col == 3) return cfg.isMaxEditable();
            if (col == 4) return cfg.isThresholdEditable();
            
            return false;
        }

        @Override
        public Object getValueAt(int row, int col) {
            if (row >= attrList.size()) return null;
            String attr = attrList.get(row);
            RawDataConfig cfg = rawDataConfigs.get(attr);
            if (cfg == null) return null;
            switch (col) {
                case 0: return attr;
                case 1: return cfg.getMode();
                case 2: return cfg.getMin();
                case 3: return cfg.getMax();
                case 4: return cfg.getThreshold();
            }
            return null;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == 0 || value == null) return;
            String attr = attrList.get(row);
            RawDataConfig cfg = rawDataConfigs.get(attr);
            if (cfg == null) return;
            
            if (col == 1) {
                if (value instanceof Mode) {
                    cfg.setMode((Mode) value);
                    fireTableRowsUpdated(row, row);
                    matrixModel.fireTableDataChanged();  // ✅ Update matrix colors in real-time
                }
            } else {
                try {
                    double d = Double.parseDouble(
                            value.toString().trim().replace(",", "."));
                    switch (col) {
                        case 2: cfg.setMin(d);       break;
                        case 3: cfg.setMax(d);       break;
                        case 4: cfg.setThreshold(d); break;
                    }
                    fireTableCellUpdated(row, col);
                    matrixModel.fireTableDataChanged();  // ✅ Already present - updates colors
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Config Cell Renderer
    // ═════════════════════════════════════════════════════════════════════════

    private class ConfigCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {

            super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, col);
            setHorizontalAlignment(col == 0 ? SwingConstants.LEFT : SwingConstants.CENTER);

            // ✅ Ignore selection - always show normal colors
            if (col == 0) {
                setBackground(COLOR_HEADER);
                setFont(getFont().deriveFont(Font.BOLD));
            } else if (!table.isCellEditable(row, col)) {
                setBackground(COLOR_DISABLED);
                setForeground(Color.GRAY);
                setFont(getFont().deriveFont(Font.PLAIN));
            } else if (col == 4) {
                setBackground(new Color(255, 255, 220));
                setForeground(Color.BLACK);
                setFont(getFont().deriveFont(Font.BOLD));
            } else {
                setBackground(Color.WHITE);
                setForeground(Color.BLACK);
                setFont(getFont().deriveFont(Font.PLAIN));
            }
            
            return this;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Mode Renderer: Display 3 radio buttons
    // ═════════════════════════════════════════════════════════════════════════

    private class ModeRenderer extends JPanel implements TableCellRenderer {
        private JRadioButton rbThreshold, rbMinMax, rbOther;
        
        ModeRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 15, 5));
            setOpaque(true);
            
            rbThreshold = new JRadioButton("Threshold");
            rbMinMax    = new JRadioButton("Min/Max");
            rbOther     = new JRadioButton("Other");
            
            // ✅ NO ButtonGroup in renderer - just display state
            // ButtonGroup would link all rows together (bug)
            
            add(rbThreshold);
            add(rbMinMax);
            add(rbOther);
        }
        
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            
            Mode mode = (value instanceof Mode) ? (Mode) value : Mode.THRESHOLD;
            
            // Simply reflect the mode state for THIS row
            rbThreshold.setSelected(mode == Mode.THRESHOLD);
            rbMinMax.setSelected(mode == Mode.MIN_MAX);
            rbOther.setSelected(mode == Mode.OTHER);
            
            // ✅ Remove blue selection background
            setBackground(Color.WHITE);
            
            return this;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Mode Editor: Edit mode via radio buttons
    // ═════════════════════════════════════════════════════════════════════════

    private class ModeEditor extends AbstractCellEditor implements TableCellEditor {
        private JPanel panel;
        private JRadioButton rbThreshold, rbMinMax, rbOther;
        private Mode selectedMode;
        
        ModeEditor() {
            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
            panel.setOpaque(true);
            panel.setBackground(new Color(255, 255, 200));
            
            rbThreshold = new JRadioButton("Threshold");
            rbMinMax    = new JRadioButton("Min/Max");
            rbOther     = new JRadioButton("Other");
            
            ButtonGroup group = new ButtonGroup();
            group.add(rbThreshold);
            group.add(rbMinMax);
            group.add(rbOther);
            
            ActionListener listener = new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (rbThreshold.isSelected()) selectedMode = Mode.THRESHOLD;
                    else if (rbMinMax.isSelected()) selectedMode = Mode.MIN_MAX;
                    else if (rbOther.isSelected()) selectedMode = Mode.OTHER;
                    fireEditingStopped();
                }
            };
            
            rbThreshold.addActionListener(listener);
            rbMinMax.addActionListener(listener);
            rbOther.addActionListener(listener);
            
            panel.add(rbThreshold);
            panel.add(rbMinMax);
            panel.add(rbOther);
        }
        
        // ✅ Single click to start editing
        @Override
        public boolean isCellEditable(java.util.EventObject e) {
            if (e instanceof java.awt.event.MouseEvent) {
                return ((java.awt.event.MouseEvent) e).getClickCount() >= 1;
            }
            return true;
        }
        
        @Override
        public Object getCellEditorValue() {
            return selectedMode;
        }
        
        @Override
        public Component getTableCellEditorComponent(
                JTable table, Object value,
                boolean isSelected, int row, int col) {
            
            Mode mode = (value instanceof Mode) ? (Mode) value : Mode.THRESHOLD;
            selectedMode = mode;
            
            rbThreshold.setSelected(mode == Mode.THRESHOLD);
            rbMinMax.setSelected(mode == Mode.MIN_MAX);
            rbOther.setSelected(mode == Mode.OTHER);
            
            return panel;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Matrix Table Model
    // ═════════════════════════════════════════════════════════════════════════

    private class MatrixTableModel extends AbstractTableModel {

        List<String> attrList = new ArrayList<>();
        List<String> objList  = new ArrayList<>();

        void refresh() {
            attrList = new ArrayList<>(rawDataConfigs.keySet());
            objList  = new ArrayList<>(continuousValues.keySet());
            fireTableStructureChanged();
        }

        @Override public int getRowCount()    { return objList.size(); }
        @Override public int getColumnCount() { return attrList.size() + 1; }

        @Override
        public String getColumnName(int col) {
            return col == 0 ? "Object" : attrList.get(col - 1);
        }

        @Override
        public boolean isCellEditable(int row, int col) { return col != 0; }

        @Override
        public Object getValueAt(int row, int col) {
            if (col == 0) return objList.get(row);
            String obj  = objList.get(row);
            String attr = attrList.get(col - 1);
            Map<String, Double> vals = continuousValues.get(obj);
            return (vals != null) ? vals.get(attr) : null;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == 0) return;
            String obj  = objList.get(row);
            String attr = attrList.get(col - 1);
            Map<String, Double> vals = continuousValues.get(obj);
            if (vals == null) {
                vals = new LinkedHashMap<>();
                continuousValues.put(obj, vals);
            }
            String text = (value == null) ? "" : value.toString().trim();
            if (text.isEmpty()) {
                vals.remove(attr);
            } else {
                try {
                    vals.put(attr, Double.parseDouble(text.replace(",", ".")));
                } catch (NumberFormatException ignored) {}
            }
            fireTableCellUpdated(row, col);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Matrix Cell Renderer
    // ═════════════════════════════════════════════════════════════════════════

    private class MatrixCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {

            String display = "";
            if (col == 0) {
                display = (value != null) ? value.toString() : "";
            } else if (value instanceof Double) {
                display = String.valueOf(value);
            }
            super.getTableCellRendererComponent(
                    table, display, isSelected, hasFocus, row, col);

            setHorizontalAlignment(col == 0 ? SwingConstants.LEFT : SwingConstants.CENTER);

            // ✅ Show selection with distinct background color
            if (isSelected) {
                // Selected cells: light blue/gray background
                setBackground(new Color(184, 207, 229));  // Light blue
                setForeground(Color.BLACK);
            } else {
                // Non-selected cells: normal colors (green/red/gray)
                if (col == 0) {
                    setBackground(COLOR_HEADER);
                    setFont(getFont().deriveFont(Font.BOLD));
                } else if (value == null) {
                    setBackground(COLOR_EMPTY);
                    setFont(getFont().deriveFont(Font.PLAIN));
                } else {
                    String attr = matrixModel.attrList.get(col - 1);
                    RawDataConfig cfg = rawDataConfigs.get(attr);
                    if (cfg != null) {
                        double d = (Double) value;
                        // ✅ Use shouldBeChecked() for coloring
                        setBackground(cfg.shouldBeChecked(d) ? COLOR_ABOVE : COLOR_BELOW);
                    } else {
                        setBackground(COLOR_EMPTY);
                    }
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
            }
            
            return this;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Double Cell Editor
    // ═════════════════════════════════════════════════════════════════════════

    private class DoubleCellEditor extends AbstractCellEditor implements TableCellEditor {

        private final JTextField field = new JTextField();

        DoubleCellEditor() {
            field.setHorizontalAlignment(JTextField.CENTER);
            field.setBorder(BorderFactory.createLineBorder(COLOR_APPLY_BG, 2));
        }

        @Override
        public boolean isCellEditable(EventObject e) {
            // Require double-click to start editing (allows drag-select)
            if (e instanceof MouseEvent) {
                return ((MouseEvent) e).getClickCount() >= 2;
            }
            return true;
        }

        @Override
        public Object getCellEditorValue() {
            return field.getText().trim();
        }

        @Override
        public Component getTableCellEditorComponent(
                JTable table, Object value,
                boolean isSelected, int row, int col) {
            field.setText(value instanceof Double ? String.valueOf(value) : "");
            field.selectAll();
            return field;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Add/Remove Attributes and Objects
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Add a new attribute to the context and raw data configuration.
     */
    private void addAttribute() {
        String attrName = JOptionPane.showInputDialog(this,
                "Enter new attribute name:",
                "Add Attribute",
                JOptionPane.PLAIN_MESSAGE);
        
        if (attrName == null || attrName.trim().isEmpty()) {
            return;  // User cancelled or entered empty name
        }
        
        attrName = attrName.trim();
        
        // Check if attribute already exists
        for (int i = 0; i < state.context.getAttributeCount(); i++) {
            if (state.context.getAttributeAtIndex(i).equals(attrName)) {
                JOptionPane.showMessageDialog(this,
                        "Attribute '" + attrName + "' already exists.",
                        "Duplicate Attribute",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        
        // Add to Context Editor
        state.context.addAttribute(attrName);
        
        // Sync and refresh
        syncFromContext();
        state.contextChanged();
        
        JOptionPane.showMessageDialog(this,
                "Attribute '" + attrName + "' added successfully.",
                "Attribute Added",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Remove selected attribute from context and raw data configuration.
     */
    private void removeAttribute() {
        // Get selected column in matrix table (like for objects)
        int selectedCol = matrixTable.getSelectedColumn();
        
        if (selectedCol < 0) {
            JOptionPane.showMessageDialog(this,
                    "Please select an attribute to remove from the matrix table.",
                    "No Selection",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // Skip first column (Object names)
        if (selectedCol == 0) {
            JOptionPane.showMessageDialog(this,
                    "Cannot remove the Object column.\n\nPlease select an attribute column (woman, man, girl, boy...).",
                    "Invalid Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Get attribute name from column header
        String attrName = matrixTable.getColumnName(selectedCol);
        
        int confirm = JOptionPane.showConfirmDialog(this,
                "Remove attribute '" + attrName + "'?\n\n" +
                "This will also remove it from the Context Editor and all its values.",
                "Confirm Removal",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        // Remove from Context Editor
        state.context.removeAttribute(attrName);
        
        // Sync and refresh
        syncFromContext();
        state.contextChanged();
        
        JOptionPane.showMessageDialog(this,
                "Attribute '" + attrName + "' removed successfully.",
                "Attribute Removed",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Add a new object to the context.
     */
    private void addObject() {
        String objName = JOptionPane.showInputDialog(this,
                "Enter new object name:",
                "Add Object",
                JOptionPane.PLAIN_MESSAGE);
        
        if (objName == null || objName.trim().isEmpty()) {
            return;  // User cancelled or entered empty name
        }
        
        objName = objName.trim();
        
        // Check if object already exists
        for (int i = 0; i < state.context.getObjectCount(); i++) {
            if (state.context.getObjectAtIndex(i).getIdentifier().equals(objName)) {
                JOptionPane.showMessageDialog(this,
                        "Object '" + objName + "' already exists.",
                        "Duplicate Object",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        
        // Add to Context Editor
        try {
            state.context.addObject(new de.tudresden.inf.tcs.fcalib.FullObject(objName));
        } catch (de.tudresden.inf.tcs.fcaapi.exception.IllegalObjectException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to add object:\n\n" + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Sync and refresh
        syncFromContext();
        state.contextChanged();
        
        JOptionPane.showMessageDialog(this,
                "Object '" + objName + "' added successfully.",
                "Object Added",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Remove selected object from context.
     */
    private void removeObject() {
        // Get selected row in matrix table
        int selectedRow = matrixTable.getSelectedRow();
        
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this,
                    "Please select an object to remove from the matrix table.",
                    "No Selection",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        String objName = (String) matrixModel.getValueAt(selectedRow, 0);
        
        int confirm = JOptionPane.showConfirmDialog(this,
                "Remove object '" + objName + "'?\n\n" +
                "This will also remove it from the Context Editor and all its values.",
                "Confirm Removal",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        // Find and remove object from Context Editor
        try {
            for (int i = 0; i < state.context.getObjectCount(); i++) {
                if (state.context.getObjectAtIndex(i).getIdentifier().equals(objName)) {
                    state.context.removeObject(state.context.getObjectAtIndex(i));
                    break;
                }
            }
        } catch (de.tudresden.inf.tcs.fcaapi.exception.IllegalObjectException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to remove object:\n\n" + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Sync and refresh
        syncFromContext();
        state.contextChanged();
        
        JOptionPane.showMessageDialog(this,
                "Object '" + objName + "' removed successfully.",
                "Object Removed",
                JOptionPane.INFORMATION_MESSAGE);
    }
}