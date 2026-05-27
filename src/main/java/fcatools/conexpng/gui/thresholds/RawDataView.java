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
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;

import fcatools.conexpng.Conf;
import fcatools.conexpng.Conf.ContextChangeEvent;
import fcatools.conexpng.ContextChangeEvents;
import fcatools.conexpng.gui.View;
import fcatools.conexpng.gui.lattice.LatticeGraph;
import fcatools.conexpng.gui.thresholds.RawDataConfig.Mode;

/**
 * Raw Data View - Conversion Continu -> Boolean.
 *
 * La colonne "Modalite" utilise un JPopupMenu (une instance par clic, par ligne)
 * au lieu d'un renderer JRadioButton partagé — ce qui éliminait le bug
 * "les boutons changent quand on clique n'importe où".
 *
 * Compatible Java 7 (pas de lambdas, pas de java.util.function).
 */
@SuppressWarnings("serial")
public class RawDataView extends View {

    // ── Couleurs ─────────────────────────────────────────────────────────────
    private static final Color COLOR_HEADER         = new Color(220, 220, 220);
    private static final Color COLOR_ABOVE          = new Color(200, 240, 200);
    private static final Color COLOR_BELOW          = new Color(255, 220, 220);
    private static final Color COLOR_EMPTY          = new Color(250, 250, 250);
    private static final Color COLOR_DISABLED       = new Color(235, 235, 235);
    private static final Color COLOR_APPLY_BG       = new Color(60,  120, 180);
    private static final Color COLOR_DRAG_HIGHLIGHT = new Color(100, 150, 255, 80);
    private static final Color COLOR_GEO_BG         = new Color(215, 235, 255);
    private static final Color COLOR_UNI_BG         = new Color(240, 215, 255);

    // ── Données ───────────────────────────────────────────────────────────────
    private Map<String, RawDataConfig>       rawDataConfigs   = new LinkedHashMap<String, RawDataConfig>();
    private Map<String, Map<String, Double>> continuousValues = new LinkedHashMap<String, Map<String, Double>>();

    // Parents restent dans Raw Data, enfants vont uniquement dans le Context Editor
    private Set<String> geometricParents  = new LinkedHashSet<String>();
    private Set<String> geometricChildren = new LinkedHashSet<String>();
    private Set<String> uniformParents    = new LinkedHashSet<String>();
    private Set<String> uniformChildren   = new LinkedHashSet<String>();

    // ── Composants ────────────────────────────────────────────────────────────
    private JTable           configTable;
    private JTable           matrixTable;
    private ConfigTableModel configModel;
    private MatrixTableModel matrixModel;

    private int     draggedRow = -1;
    private int     draggedCol = -1;
    private boolean isDragging = false;

    // ═════════════════════════════════════════════════════════════════════════
    // Construction
    // ═════════════════════════════════════════════════════════════════════════

    public RawDataView(Conf state) {
        super(state);
        setLayout(new BorderLayout());
        buildUI();
        syncFromContext();
    }

    private void buildUI() {
        // ── Config table ──────────────────────────────────────────────────────
        configModel = new ConfigTableModel();
        configTable = new JTable(configModel);
        configTable.setRowHeight(80);
        configTable.setFont(configTable.getFont().deriveFont(13f));
        configTable.getTableHeader().setReorderingAllowed(false);
        configTable.getTableHeader().setFont(configTable.getFont().deriveFont(Font.BOLD));
        configTable.setDefaultRenderer(Object.class, new ConfigCellRenderer());

        // Colonne 1 (Modalite) : renderer dropdown — AUCUN éditeur de cellule,
        // les clics sont gérés par setupModeClickListener() via JPopupMenu.
        configTable.getColumnModel().getColumn(1).setCellRenderer(new ModeDropdownRenderer());

        configTable.getColumnModel().getColumn(4).setCellRenderer(new ThresholdCellRenderer());
        configTable.getColumnModel().getColumn(4).setCellEditor(new ThresholdCellEditor());
        configTable.setGridColor(new Color(160, 160, 160));
        configTable.setShowGrid(true);
        configTable.setIntercellSpacing(new Dimension(1, 1));

        JScrollPane configScroll = new JScrollPane(configTable);
        configScroll.setPreferredSize(new Dimension(0, 200));
        configScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "  Raw Data Configuration  -  Cliquer sur la modalité pour changer  ",
                TitledBorder.LEFT, TitledBorder.TOP,
                configTable.getFont().deriveFont(Font.BOLD)));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        JButton addAttrButton    = makeButton("+ Add Attribute",    false);
        JButton removeAttrButton = makeButton("- Remove Attribute", false);
        JButton addObjButton     = makeButton("+ Add Object",       false);
        JButton removeObjButton  = makeButton("- Remove Object",    false);
        addAttrButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { addAttribute(); }
        });
        removeAttrButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { removeAttribute(); }
        });
        addObjButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { addObject(); }
        });
        removeObjButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { removeObject(); }
        });
        buttonPanel.add(addAttrButton);
        buttonPanel.add(removeAttrButton);
        buttonPanel.add(new JLabel("  |  "));
        buttonPanel.add(addObjButton);
        buttonPanel.add(removeObjButton);

        JPanel configPanel = new JPanel(new BorderLayout());
        configPanel.add(configScroll, BorderLayout.CENTER);
        configPanel.add(buttonPanel,  BorderLayout.SOUTH);

        // ── Matrix table ──────────────────────────────────────────────────────
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
        matrixTable.setRowSelectionAllowed(true);
        matrixTable.setColumnSelectionAllowed(true);
        matrixTable.setCellSelectionEnabled(true);
        matrixTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // Fix : effacer la sélection bleue dès que la souris est relâchée
        // (sauf si un éditeur de cellule est actif — Min, Max, Threshold).
        configTable.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                if (!configTable.isEditing()) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() { configTable.clearSelection(); }
                    });
                }
            }
        });

        setupModeClickListener();
        setupDragAndDrop();

        matrixTable.getTableHeader().addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e)  { if (e.isPopupTrigger()) showHeaderContextMenu(e); }
            public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showHeaderContextMenu(e); }
        });
        matrixTable.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e)  { if (e.isPopupTrigger()) showContextMenu(e); }
            public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showContextMenu(e); }
        });

        InputMap  im = matrixTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = matrixTable.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
        am.put("copy", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { copySelection(); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste");
        am.put("paste", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { pasteSelection(); }
        });

        JScrollPane matrixScroll = new JScrollPane(matrixTable);
        matrixScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "  Continuous Values  -  Right-click to duplicate or drag to reorder  " +
                "  |  green = will be checked  |  red = will be empty  ",
                TitledBorder.LEFT, TitledBorder.TOP,
                matrixTable.getFont().deriveFont(Font.BOLD)));

        // ── Barre du bas ──────────────────────────────────────────────────────
        JButton applyButton  = makeButton("Apply to Context Editor & Lattice", true);
        applyButton.setBackground(COLOR_APPLY_BG);
        applyButton.setPreferredSize(new Dimension(280, 36));
        applyButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { applyRawData(); }
        });

        JButton importButton = makeButton("Import Excel...", true);
        importButton.setBackground(new Color(70, 140, 70));
        importButton.setPreferredSize(new Dimension(150, 36));
        importButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { importExcelFile(); }
        });

        JButton exportButton = makeButton("Export Excel...", true);
        exportButton.setBackground(new Color(33, 150, 243));
        exportButton.setPreferredSize(new Dimension(150, 36));
        exportButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { exportExcelFile(); }
        });

        JLabel hintLabel = new JLabel("  Après Apply, aller dans l'onglet Lattice pour régénérer le treillis.");
        hintLabel.setForeground(new Color(90, 90, 90));
        hintLabel.setFont(hintLabel.getFont().deriveFont(12f));

        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        southPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(140, 140, 140)));
        southPanel.add(importButton);
        southPanel.add(exportButton);
        southPanel.add(applyButton);
        southPanel.add(hintLabel);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, configPanel, matrixScroll);
        split.setResizeWeight(0.35);
        split.setDividerSize(6);
        split.setBorder(new EmptyBorder(4, 4, 4, 4));

        add(split,      BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
    }

    // ── Petit helper pour créer des boutons uniformes ─────────────────────────
    private JButton makeButton(String label, boolean bold) {
        JButton b = new JButton(label);
        b.setFont(b.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, bold ? 13f : 11f));
        if (bold) {
            b.setForeground(Color.BLACK);
            b.setOpaque(true);
            b.setBorderPainted(false);
            b.setFocusPainted(false);
        }
        return b;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Mode helpers (noms affichés + couleurs)
    // ═════════════════════════════════════════════════════════════════════════

    /** Nom lisible d'un mode pour l'interface. */
    private static String modeName(Mode m) {
        switch (m) {
            case THRESHOLD: return "Seuil";
            case MIN_MAX:   return "Min / Max";
            case GEOMETRIC: return "Géométrique";
            case UNIFORME:  return "Uniforme";
            case OTHER:     return "Autre";
            default:        return m.name();
        }
    }

    /** Couleur de fond associée à un mode. */
    private static Color colorForMode(Mode m) {
        switch (m) {
            case THRESHOLD: return new Color(255, 245, 210);
            case MIN_MAX:   return new Color(210, 255, 210);
            case GEOMETRIC: return COLOR_GEO_BG;
            case UNIFORME:  return COLOR_UNI_BG;
            case OTHER:     return new Color(235, 235, 235);
            default:        return Color.WHITE;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Apply Raw Data
    // ═════════════════════════════════════════════════════════════════════════

    private void applyRawData() {
        if (configTable.isEditing()) configTable.getCellEditor().stopCellEditing();
        if (matrixTable.isEditing())  matrixTable.getCellEditor().stopCellEditing();

        List<String> attrs = getAttributeList();
        List<String> objs  = getObjectList();

        if (attrs.isEmpty() || objs.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Le contexte est vide. Définissez d'abord des attributs et des objets.",
                    "Rien à appliquer", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 1. Validation des configs GEOMETRIC et UNIFORME
        for (String attr : attrs) {
            RawDataConfig cfg = rawDataConfigs.get(attr);
            if (cfg == null) continue;
            if (cfg.getMode() == Mode.GEOMETRIC) {
                String err = cfg.validateGeometric();
                if (err != null) {
                    JOptionPane.showMessageDialog(this,
                            "Config invalide pour \"" + attr + "\" (Géométrique) :\n\n" + err +
                            "\n\nCorrigez Min et Max dans le tableau de configuration.",
                            "Erreur - Suite Géométrique", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            if (cfg.getMode() == Mode.UNIFORME) {
                String err = cfg.validateUniform();
                if (err != null) {
                    JOptionPane.showMessageDialog(this,
                            "Config invalide pour \"" + attr + "\" (Uniforme) :\n\n" + err +
                            "\n\nCorrigez Min et Max dans le tableau de configuration.",
                            "Erreur - Découpage Uniforme", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
        }

        // 2. Traitement des attributs GEOMETRIC et UNIFORME
        Set<String> specialAttrs = new LinkedHashSet<String>();
        StringBuilder specialMsg = new StringBuilder();

        for (String attr : new ArrayList<String>(attrs)) {
            RawDataConfig cfg = rawDataConfigs.get(attr);
            if (cfg == null) continue;
            if (cfg.getMode() == Mode.GEOMETRIC) {
                specialAttrs.add(attr);
                applyGeometricAttribute(attr, cfg, objs);
                specialMsg.append("  * \"").append(attr).append("\" (Géo) -> ")
                          .append(cfg.getNumberOfClasses()).append(" colonnes créées\n");
            } else if (cfg.getMode() == Mode.UNIFORME) {
                specialAttrs.add(attr);
                applyUniformAttribute(attr, cfg, objs);
                specialMsg.append("  * \"").append(attr).append("\" (Uni) -> ")
                          .append(cfg.getNumberOfClasses()).append(" colonnes créées\n");
            }
        }

        // 3. Traitement des attributs normaux (THRESHOLD / MIN_MAX / OTHER)
        int applied = 0, skipped = 0;
        for (String obj : objs) {
            Map<String, Double> objValues = continuousValues.get(obj);
            for (String attr : attrs) {
                if (specialAttrs.contains(attr)) continue;
                RawDataConfig cfg   = rawDataConfigs.get(attr);
                Double        value = (objValues != null) ? objValues.get(attr) : null;
                if (value == null || cfg == null) { skipped++; continue; }
                boolean shouldHave = cfg.shouldBeChecked(value);
                boolean hasNow     = state.context.objectHasAttribute(
                        state.context.getObject(obj), attr);
                if (shouldHave != hasNow) state.context.toggleAttributeForObject(attr, obj);
                applied++;
            }
        }

        state.lattice  = new LatticeGraph();
        state.concepts = new java.util.HashSet();
        state.contextChanged();

        StringBuilder msg = new StringBuilder("Terminé !\n\n");
        if (specialMsg.length() > 0) {
            msg.append("Colonnes créées :\n").append(specialMsg).append("\n");
        }
        msg.append("Cellules traitées : ").append(applied);
        if (skipped > 0) msg.append("\nCellules ignorées (vides) : ").append(skipped);
        msg.append("\n\nVérifiez dans Context Editor, puis générez le treillis.");
        JOptionPane.showMessageDialog(this, msg.toString(), "Raw Data Appliqué",
                JOptionPane.INFORMATION_MESSAGE);
    }

    // ── Application suite géométrique ─────────────────────────────────────────

    private void applyGeometricAttribute(String attr, RawDataConfig cfg, List<String> objs) {
        double min = cfg.getMin();
        double max = cfg.getMax();
        int    n   = cfg.getNumberOfClasses();
        List<String> classLabels = GeometricScale.computeClassLabels(attr, min, max, n);

        geometricParents.add(attr);
        for (String cl : classLabels) geometricChildren.add(cl);

        // Ajouter les colonnes de classe dans le contexte
        for (String classAttr : classLabels) {
            boolean exists = false;
            for (int i = 0; i < state.context.getAttributeCount(); i++) {
                if (state.context.getAttributeAtIndex(i).equals(classAttr)) { exists = true; break; }
            }
            if (!exists) state.context.addAttribute(classAttr);
        }

        // Affecter chaque objet à sa classe (one-hot)
        for (String obj : objs) {
            Map<String, Double> objValues = continuousValues.get(obj);
            if (objValues == null) continue;
            Double value = objValues.get(attr);
            if (value == null) continue;
            int cls = GeometricScale.classifyValue(value, min, max, n);
            for (int c = 0; c < classLabels.size(); c++) {
                String  classAttr  = classLabels.get(c);
                boolean shouldHave = (c + 1 == cls);
                boolean hasNow     = state.context.objectHasAttribute(
                        state.context.getObject(obj), classAttr);
                if (shouldHave != hasNow) state.context.toggleAttributeForObject(classAttr, obj);
            }
        }

        // Retirer l'attribut original du contexte (remplacé par les colonnes de classe)
        for (int i = 0; i < state.context.getAttributeCount(); i++) {
            if (state.context.getAttributeAtIndex(i).equals(attr)) {
                state.context.removeAttribute(attr);
                break;
            }
        }
    }

    // ── Application découpage uniforme ────────────────────────────────────────

    private void applyUniformAttribute(String attr, RawDataConfig cfg, List<String> objs) {
        double min = cfg.getMin();
        double max = cfg.getMax();
        int    n   = cfg.getNumberOfClasses();
        List<String> classLabels = UniformScale.computeClassLabels(attr, min, max, n);

        uniformParents.add(attr);
        for (String cl : classLabels) uniformChildren.add(cl);

        // Ajouter les colonnes de classe dans le contexte
        for (String classAttr : classLabels) {
            boolean exists = false;
            for (int i = 0; i < state.context.getAttributeCount(); i++) {
                if (state.context.getAttributeAtIndex(i).equals(classAttr)) { exists = true; break; }
            }
            if (!exists) state.context.addAttribute(classAttr);
        }

        // Affecter chaque objet à sa classe (one-hot)
        for (String obj : objs) {
            Map<String, Double> objValues = continuousValues.get(obj);
            if (objValues == null) continue;
            Double value = objValues.get(attr);
            if (value == null) continue;
            int cls = UniformScale.classifyValue(value, min, max, n);
            for (int c = 0; c < classLabels.size(); c++) {
                String  classAttr  = classLabels.get(c);
                boolean shouldHave = (c + 1 == cls);
                boolean hasNow     = state.context.objectHasAttribute(
                        state.context.getObject(obj), classAttr);
                if (shouldHave != hasNow) state.context.toggleAttributeForObject(classAttr, obj);
            }
        }

        // Retirer l'attribut original du contexte
        for (int i = 0; i < state.context.getAttributeCount(); i++) {
            if (state.context.getAttributeAtIndex(i).equals(attr)) {
                state.context.removeAttribute(attr);
                break;
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ModeDropdownRenderer — remplace l'ancien ModeRenderer radio-button
    //
    // Affiche simplement le nom du mode courant + "▼" pour indiquer qu'un clic
    // ouvre un menu déroulant. Chaque appel crée une cellule statique, sans
    // état partagé → zéro contamination entre les lignes.
    // ═════════════════════════════════════════════════════════════════════════

    private class ModeDropdownRenderer extends DefaultTableCellRenderer {
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int col) {
            Mode m = (value instanceof Mode) ? (Mode) value : Mode.THRESHOLD;
            super.getTableCellRendererComponent(table, modeName(m) + "  ▼",
                    isSelected, hasFocus, row, col);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(getFont().deriveFont(Font.BOLD, 12f));
            if (isSelected) {
                setBackground(new Color(150, 180, 220));
            } else {
                setBackground(colorForMode(m));
            }
            setForeground(new Color(30, 30, 80));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(140, 140, 200), 1),
                    BorderFactory.createEmptyBorder(2, 8, 2, 8)));
            setToolTipText("Cliquer pour changer la modalité");
            return this;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ThresholdCellRenderer — colonne 4 (Threshold / n classes)
    // ═════════════════════════════════════════════════════════════════════════

    private class ThresholdCellRenderer extends DefaultTableCellRenderer {
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int col) {
            if (row >= configModel.attrList.size()) return this;
            String        attr = configModel.attrList.get(row);
            RawDataConfig cfg  = rawDataConfigs.get(attr);

            if (cfg != null && (cfg.getMode() == Mode.GEOMETRIC || cfg.getMode() == Mode.UNIFORME)) {
                Color bgColor = cfg.getMode() == Mode.GEOMETRIC ? COLOR_GEO_BG : COLOR_UNI_BG;
                JPanel panel = new JPanel(new BorderLayout(4, 2));
                panel.setBackground(isSelected ? new Color(184, 207, 229) : bgColor);
                panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

                JLabel topLabel = new JLabel("n = " + cfg.getNumberOfClasses() + " classes");
                topLabel.setFont(topLabel.getFont().deriveFont(Font.BOLD, 12f));
                topLabel.setForeground(cfg.getMode() == Mode.GEOMETRIC
                        ? new Color(30, 70, 140) : new Color(80, 30, 140));

                JLabel previewLabel = new JLabel(buildPreviewText(cfg));
                previewLabel.setFont(previewLabel.getFont().deriveFont(Font.ITALIC, 10f));
                previewLabel.setForeground(cfg.getMode() == Mode.GEOMETRIC
                        ? new Color(70, 100, 160) : new Color(110, 50, 160));

                panel.add(topLabel,     BorderLayout.NORTH);
                panel.add(previewLabel, BorderLayout.CENTER);
                return panel;
            }

            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            setHorizontalAlignment(SwingConstants.CENTER);
            if (!table.isCellEditable(row, col)) {
                setBackground(COLOR_DISABLED);
                setForeground(Color.GRAY);
            } else {
                setBackground(new Color(255, 255, 220));
                setForeground(Color.BLACK);
                setFont(getFont().deriveFont(Font.BOLD));
            }
            return this;
        }

        private String buildPreviewText(RawDataConfig cfg) {
            try {
                List<Double> t;
                if (cfg.getMode() == Mode.GEOMETRIC) {
                    String err = cfg.validateGeometric();
                    if (err != null) return "<html><i style='color:red'>! Min/Max invalides</i></html>";
                    t = GeometricScale.computeThresholds(cfg.getMin(), cfg.getMax(), cfg.getNumberOfClasses());
                } else {
                    String err = cfg.validateUniform();
                    if (err != null) return "<html><i style='color:red'>! Min/Max invalides</i></html>";
                    t = UniformScale.computeThresholds(cfg.getMin(), cfg.getMax(), cfg.getNumberOfClasses());
                }
                StringBuilder sb = new StringBuilder("<html>seuils : ");
                for (int i = 0; i < t.size(); i++) {
                    sb.append(String.format("%.3g", t.get(i)));
                    if (i < t.size() - 1) sb.append(", ");
                }
                return sb.toString() + "</html>";
            } catch (Exception ex) {
                return "<html><i style='color:red'>! erreur calcul</i></html>";
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ThresholdCellEditor — édite n (spinner) pour GEOMETRIC/UNIFORME,
    //                        ou la valeur de seuil (textField) pour THRESHOLD
    // ═════════════════════════════════════════════════════════════════════════

    private class ThresholdCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JTextField textField = new JTextField();
        private String currentAttr;

        ThresholdCellEditor() {
            textField.setHorizontalAlignment(JTextField.CENTER);
            textField.setBorder(BorderFactory.createLineBorder(COLOR_APPLY_BG, 2));
        }

        public boolean isCellEditable(EventObject e) {
            if (e instanceof MouseEvent) {
                int row = configTable.rowAtPoint(((MouseEvent) e).getPoint());
                if (row >= 0 && row < configModel.attrList.size()) {
                    String attr = configModel.attrList.get(row);
                    RawDataConfig cfg = rawDataConfigs.get(attr);
                    if (cfg != null && (cfg.getMode() == Mode.GEOMETRIC
                                     || cfg.getMode() == Mode.UNIFORME)) return true;
                }
                return ((MouseEvent) e).getClickCount() >= 2;
            }
            return true;
        }

        public Object getCellEditorValue() {
            RawDataConfig cfg = (currentAttr != null) ? rawDataConfigs.get(currentAttr) : null;
            if (cfg != null && (cfg.getMode() == Mode.GEOMETRIC || cfg.getMode() == Mode.UNIFORME))
                return cfg.getNumberOfClasses();
            return textField.getText().trim();
        }

        public Component getTableCellEditorComponent(
                JTable table, Object value, boolean isSelected, int row, int col) {
            currentAttr = (row < configModel.attrList.size()) ? configModel.attrList.get(row) : null;
            final RawDataConfig cfg = (currentAttr != null) ? rawDataConfigs.get(currentAttr) : null;

            if (cfg != null && (cfg.getMode() == Mode.GEOMETRIC || cfg.getMode() == Mode.UNIFORME)) {
                // Auto-détection min/max si encore à 0
                if (cfg.getMin() == 0.0 && cfg.getMax() == 0.0) {
                    double[] mm = computeMinMaxForAttr(currentAttr);
                    if (mm != null) {
                        cfg.setMin(mm[0]); cfg.setMax(mm[1]);
                        configModel.fireTableRowsUpdated(row, row);
                    }
                }
                return buildClassEditorPanel(cfg, row, col);
            }
            textField.setText(value instanceof Double ? String.valueOf(value) : "");
            textField.selectAll();
            return textField;
        }

        /** Panneau spinner partagé par GEOMETRIC et UNIFORME. */
        private Component buildClassEditorPanel(final RawDataConfig cfg, final int row, final int col) {
            final boolean isGeo = cfg.getMode() == Mode.GEOMETRIC;
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(isGeo ? COLOR_GEO_BG : COLOR_UNI_BG);
            panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

            JPanel rowTop = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
            rowTop.setOpaque(false);
            rowTop.add(new JLabel("n = "));

            // Max = Integer.MAX_VALUE pour détecter la saisie > 1000 dans le ChangeListener
            // et afficher un message d'erreur explicite.
            SpinnerNumberModel spinModel = new SpinnerNumberModel(cfg.getNumberOfClasses(), 2, Integer.MAX_VALUE, 1);
            final JSpinner spinner = new JSpinner(spinModel);
            spinner.setPreferredSize(new Dimension(68, 22));
            spinner.setToolTipText("Nombre de classes (min. 2 — max. 1000)");

            final JLabel previewLbl = new JLabel(buildEditorPreview(cfg));
            previewLbl.setFont(previewLbl.getFont().deriveFont(Font.ITALIC, 10f));
            previewLbl.setForeground(isGeo ? new Color(40, 80, 160) : new Color(100, 40, 160));

            // Java 7 : la variable utilisée dans l'inner class doit être final
            spinner.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    int n = (Integer) spinner.getValue();
                    // Validation explicite : message d'erreur si > 1000
                    if (n > 1000) {
                        JOptionPane.showMessageDialog(
                                configTable,
                                "Le nombre de classes ne peut pas dépasser 1000.\n"
                                + "Valeur réinitialisée à 1000.",
                                "Limite dépassée", JOptionPane.WARNING_MESSAGE);
                        spinner.setValue(1000);
                        cfg.setNumberOfClasses(1000);
                        previewLbl.setText(buildEditorPreview(cfg));
                        configModel.fireTableCellUpdated(row, col);
                        matrixModel.fireTableDataChanged();
                        return;
                    }
                    cfg.setNumberOfClasses(n);
                    previewLbl.setText(buildEditorPreview(cfg));
                    configModel.fireTableCellUpdated(row, col);
                    // Mise à jour en temps réel des couleurs dans la matrice
                    matrixModel.fireTableDataChanged();
                }
            });

            rowTop.add(spinner);
            rowTop.add(new JLabel(" classes"));
            JLabel maxHint = new JLabel("  (max. 1000)");
            maxHint.setFont(maxHint.getFont().deriveFont(Font.ITALIC, 9f));
            maxHint.setForeground(new Color(140, 100, 140));
            rowTop.add(maxHint);
            panel.add(rowTop);
            panel.add(previewLbl);
            return panel;
        }

        private String buildEditorPreview(RawDataConfig cfg) {
            try {
                List<Double> t;
                if (cfg.getMode() == Mode.GEOMETRIC) {
                    if (cfg.validateGeometric() != null)
                        return "<html><i style='color:red'>! Min/Max invalides</i></html>";
                    t = GeometricScale.computeThresholds(cfg.getMin(), cfg.getMax(), cfg.getNumberOfClasses());
                } else {
                    if (cfg.validateUniform() != null)
                        return "<html><i style='color:red'>! Min/Max invalides</i></html>";
                    t = UniformScale.computeThresholds(cfg.getMin(), cfg.getMax(), cfg.getNumberOfClasses());
                }
                StringBuilder sb = new StringBuilder("<html>seuils : ");
                for (int i = 0; i < t.size(); i++) {
                    sb.append(String.format("%.3g", t.get(i)));
                    if (i < t.size() - 1) sb.append(", ");
                }
                return sb.toString() + "</html>";
            } catch (Exception ex) {
                return "<html><i style='color:red'>! erreur calcul</i></html>";
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // setupModeClickListener — JPopupMenu par clic, une instance par ligne
    //
    // Pourquoi un popup et non un CellEditor ?
    //   Un CellEditor est réutilisé par toutes les lignes ; quand la cellule
    //   perd le focus il peut commettre une valeur périmée sur la mauvaise ligne.
    //   Ici chaque clic crée un JPopupMenu neuf, avec un ActionListener qui
    //   capture le cfg de cette ligne précise via une variable final — aucun
    //   état partagé, zéro contamination entre les lignes.
    // ═════════════════════════════════════════════════════════════════════════

    private void setupModeClickListener() {
        configTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int col = configTable.columnAtPoint(e.getPoint());
                int row = configTable.rowAtPoint(e.getPoint());

                // Uniquement la colonne 1 (Modalite)
                if (col != 1 || row < 0 || row >= configModel.attrList.size()) return;
                if (configTable.isEditing()) configTable.getCellEditor().stopCellEditing();

                final String        attr    = configModel.attrList.get(row);
                final RawDataConfig cfg     = rawDataConfigs.get(attr);
                if (cfg == null) return;

                final int finalRow = row;
                JPopupMenu popup = new JPopupMenu();

                // Titre non-cliquable
                JLabel title = new JLabel("  Modalité de « " + attr + " »");
                title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
                title.setForeground(new Color(60, 60, 120));
                title.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
                popup.add(title);
                popup.addSeparator();

                final Mode[] modes = Mode.values();
                for (int mi = 0; mi < modes.length; mi++) {
                    final Mode m = modes[mi];
                    // Texte simple : "✓  Seuil" pour le mode courant, "   Seuil" sinon
                    // PAS de setBackground/setOpaque : WebLaF ne repeint pas le fond
                    // des JMenuItem et laisse du texte fantôme (superposition).
                    String prefix = (cfg.getMode() == m) ? "✓  " : "    ";
                    JMenuItem item = new JMenuItem(prefix + modeName(m));
                    item.setFont(cfg.getMode() == m
                            ? item.getFont().deriveFont(Font.BOLD, 12f)
                            : item.getFont().deriveFont(Font.PLAIN, 12f));

                    item.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent ae) {
                            cfg.setMode(m);
                            if (m == Mode.GEOMETRIC || m == Mode.UNIFORME) {
                                double[] mm = computeMinMaxForAttr(attr);
                                if (mm != null) { cfg.setMin(mm[0]); cfg.setMax(mm[1]); }
                            }
                            configModel.fireTableRowsUpdated(finalRow, finalRow);
                            matrixModel.fireTableDataChanged();
                        }
                    });
                    popup.add(item);
                }
                popup.show(configTable, e.getX(), e.getY());
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Drag & Drop (inchangé par rapport à la version d'origine)
    // ═════════════════════════════════════════════════════════════════════════

    private void setupDragAndDrop() {
        MouseAdapter tableDrag = new MouseAdapter() {
            private Point startPoint;
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                startPoint = e.getPoint();
                int sr = matrixTable.rowAtPoint(startPoint);
                int sc = matrixTable.columnAtPoint(startPoint);
                if      (sc == 0 && sr >= 0) { draggedRow = sr; draggedCol = -1; }
                else if (sc  > 0 && sr >= 0) { draggedCol = sc; draggedRow = -1; }
            }
            public void mouseDragged(MouseEvent e) {
                if (startPoint != null && (draggedRow >= 0 || draggedCol >= 0)) {
                    if (Math.abs(e.getX()-startPoint.x) > 5 || Math.abs(e.getY()-startPoint.y) > 5) {
                        isDragging = true;
                        matrixTable.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        matrixTable.repaint();
                    }
                }
            }
            public void mouseReleased(MouseEvent e) {
                if (isDragging) {
                    int tRow = matrixTable.rowAtPoint(e.getPoint());
                    int tCol = matrixTable.columnAtPoint(e.getPoint());
                    if      (draggedRow >= 0 && tRow >= 0 && tRow != draggedRow && tCol == 0)
                        swapObjects(draggedRow, tRow);
                    else if (draggedCol >= 0 && tCol > 0 && tCol != draggedCol)
                        swapAttributes(draggedCol, tCol);
                }
                draggedRow = -1; draggedCol = -1; isDragging = false; startPoint = null;
                matrixTable.setCursor(Cursor.getDefaultCursor());
                matrixTable.repaint();
            }
        };
        matrixTable.addMouseListener(tableDrag);
        matrixTable.addMouseMotionListener(tableDrag);

        MouseAdapter headerDrag = new MouseAdapter() {
            private Point startPoint;
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger()) return;
                startPoint = e.getPoint();
                int col = matrixTable.columnAtPoint(startPoint);
                if (col > 0) { draggedCol = col; draggedRow = -1; }
            }
            public void mouseDragged(MouseEvent e) {
                if (startPoint != null && draggedCol >= 0
                        && Math.abs(e.getX()-startPoint.x) > 5) {
                    isDragging = true;
                    matrixTable.getTableHeader().setCursor(
                            Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    matrixTable.repaint();
                }
            }
            public void mouseReleased(MouseEvent e) {
                if (isDragging && draggedCol >= 0) {
                    int tCol = matrixTable.columnAtPoint(e.getPoint());
                    if (tCol > 0 && tCol != draggedCol) swapAttributes(draggedCol, tCol);
                }
                draggedCol = -1; draggedRow = -1; isDragging = false; startPoint = null;
                matrixTable.getTableHeader().setCursor(Cursor.getDefaultCursor());
                matrixTable.repaint();
            }
        };
        matrixTable.getTableHeader().addMouseListener(headerDrag);
        matrixTable.getTableHeader().addMouseMotionListener(headerDrag);
    }

    private void swapObjects(int row1, int row2) {
        List<String> objList = new ArrayList<String>(continuousValues.keySet());
        if (row1 < 0 || row2 < 0 || row1 >= objList.size() || row2 >= objList.size()) return;
        String o1 = objList.get(row1), o2 = objList.get(row2);
        Map<String, Map<String, Double>> nv = new LinkedHashMap<String, Map<String, Double>>();
        for (int i = 0; i < objList.size(); i++) {
            String o = (i == row1) ? o2 : (i == row2) ? o1 : objList.get(i);
            nv.put(o, continuousValues.get(o));
        }
        continuousValues = nv;
        matrixModel.refresh(); state.contextChanged();
    }

    private void swapAttributes(int col1, int col2) {
        List<String> al = new ArrayList<String>(rawDataConfigs.keySet());
        int i1 = col1-1, i2 = col2-1;
        if (i1 < 0 || i2 < 0 || i1 >= al.size() || i2 >= al.size()) return;
        String a1 = al.get(i1), a2 = al.get(i2);
        Map<String, RawDataConfig> nc = new LinkedHashMap<String, RawDataConfig>();
        for (int i = 0; i < al.size(); i++) {
            String a = (i == i1) ? a2 : (i == i2) ? a1 : al.get(i);
            nc.put(a, rawDataConfigs.get(a));
        }
        rawDataConfigs = nc;
        configModel.refresh(); matrixModel.refresh(); state.contextChanged();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Context Menus & Duplication
    // ═════════════════════════════════════════════════════════════════════════

    private void showContextMenu(MouseEvent e) {
        final int row = matrixTable.rowAtPoint(e.getPoint());
        final int col = matrixTable.columnAtPoint(e.getPoint());
        if (row < 0 || col < 0) return;
        JPopupMenu menu = new JPopupMenu();
        if (col == 0) {
            JMenuItem dup = new JMenuItem("Duplicate Object");
            dup.setFont(dup.getFont().deriveFont(Font.BOLD));
            dup.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) { duplicateObject(row); }
            });
            menu.add(dup);
        } else {
            JMenuItem dup = new JMenuItem("Duplicate Attribute");
            dup.setFont(dup.getFont().deriveFont(Font.BOLD));
            dup.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) { duplicateAttribute(col); }
            });
            menu.add(dup);
        }
        menu.show(matrixTable, e.getX(), e.getY());
    }

    private void showHeaderContextMenu(MouseEvent e) {
        final int col = matrixTable.columnAtPoint(e.getPoint());
        if (col <= 0) return;
        JPopupMenu menu = new JPopupMenu();
        JMenuItem dup = new JMenuItem("Duplicate Attribute");
        dup.setFont(dup.getFont().deriveFont(Font.BOLD));
        dup.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) { duplicateAttribute(col); }
        });
        menu.add(dup);
        menu.show(matrixTable.getTableHeader(), e.getX(), e.getY());
    }

    private void duplicateAttribute(int col) {
        if (col <= 0 || col-1 >= matrixModel.attrList.size()) return;
        String orig   = matrixModel.attrList.get(col-1);
        String newAttr = findAvailableName(orig, rawDataConfigs.keySet());
        RawDataConfig oc = rawDataConfigs.get(orig);
        RawDataConfig nc = new RawDataConfig(newAttr);
        if (oc != null) {
            nc.setMode(oc.getMode()); nc.setMin(oc.getMin()); nc.setMax(oc.getMax());
            nc.setThreshold(oc.getThreshold()); nc.setNumberOfClasses(oc.getNumberOfClasses());
        }
        Map<String, RawDataConfig> nr = new LinkedHashMap<String, RawDataConfig>();
        for (String a : rawDataConfigs.keySet()) {
            nr.put(a, rawDataConfigs.get(a));
            if (a.equals(orig)) nr.put(newAttr, nc);
        }
        rawDataConfigs = nr;
        for (String obj : continuousValues.keySet()) {
            Double v = continuousValues.get(obj).get(orig);
            if (v != null) continuousValues.get(obj).put(newAttr, v);
        }
        state.context.addAttribute(newAttr);
        configModel.refresh(); matrixModel.refresh(); state.contextChanged();
        JOptionPane.showMessageDialog(this,
                "Attribut '" + orig + "' dupliqué en '" + newAttr + "'.",
                "Attribut Dupliqué", JOptionPane.INFORMATION_MESSAGE);
    }

    private void duplicateObject(int row) {
        if (row < 0 || row >= matrixModel.objList.size()) return;
        String orig   = matrixModel.objList.get(row);
        String newObj = findAvailableName(orig, continuousValues.keySet());
        Map<String, Double> nv = new LinkedHashMap<String, Double>();
        if (continuousValues.get(orig) != null) nv.putAll(continuousValues.get(orig));
        Map<String, Map<String, Double>> nm = new LinkedHashMap<String, Map<String, Double>>();
        for (String o : continuousValues.keySet()) {
            nm.put(o, continuousValues.get(o));
            if (o.equals(orig)) nm.put(newObj, nv);
        }
        continuousValues = nm;
        try {
            state.context.addObject(new de.tudresden.inf.tcs.fcalib.FullObject(newObj));
        } catch (de.tudresden.inf.tcs.fcaapi.exception.IllegalObjectException ex) {
            JOptionPane.showMessageDialog(this, "Échec : " + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }
        matrixModel.refresh(); state.contextChanged();
        JOptionPane.showMessageDialog(this,
                "Objet '" + orig + "' dupliqué en '" + newObj + "'.",
                "Objet Dupliqué", JOptionPane.INFORMATION_MESSAGE);
    }

    private String findAvailableName(String base, Set<String> existing) {
        String candidate = base + "1";
        for (int i = 2; existing.contains(candidate); i++) candidate = base + i;
        return candidate;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Sync & Context Management
    // ═════════════════════════════════════════════════════════════════════════

    private void syncFromContext() {
        List<String> attrs = getAttributeList();
        List<String> objs  = getObjectList();

        Map<String, RawDataConfig> nc = new LinkedHashMap<String, RawDataConfig>();
        for (String a : attrs) {
            // Ignorer les colonnes enfants (géo/uniforme) — elles vont dans Context Editor uniquement
            if (geometricChildren.contains(a) || uniformChildren.contains(a)) continue;
            nc.put(a, rawDataConfigs.containsKey(a) ? rawDataConfigs.get(a) : new RawDataConfig(a));
        }
        // Conserver les attributs parents (retirés du contexte après Apply, mais visibles dans Raw Data)
        for (String parent : geometricParents) {
            if (!nc.containsKey(parent) && rawDataConfigs.containsKey(parent))
                nc.put(parent, rawDataConfigs.get(parent));
        }
        for (String parent : uniformParents) {
            if (!nc.containsKey(parent) && rawDataConfigs.containsKey(parent))
                nc.put(parent, rawDataConfigs.get(parent));
        }
        rawDataConfigs = nc;

        Map<String, Map<String, Double>> nv = new LinkedHashMap<String, Map<String, Double>>();
        for (String o : objs)
            nv.put(o, continuousValues.containsKey(o)
                    ? continuousValues.get(o) : new LinkedHashMap<String, Double>());
        continuousValues = nv;

        configModel.refresh();
        matrixModel.refresh();
        // Mise à jour automatique Min/Max pour GEOMETRIC et UNIFORME
        refreshAutoMinMax();
    }

    private List<String> getAttributeList() {
        List<String> r = new ArrayList<String>();
        for (int i = 0; i < state.context.getAttributeCount(); i++)
            r.add(state.context.getAttributeAtIndex(i));
        return r;
    }

    private List<String> getObjectList() {
        List<String> r = new ArrayList<String>();
        for (int i = 0; i < state.context.getObjectCount(); i++)
            r.add(state.context.getObjectAtIndex(i).getIdentifier());
        return r;
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if (e instanceof ContextChangeEvent) {
            ContextChangeEvent cce = (ContextChangeEvent) e;
            if (cce.getName() == ContextChangeEvents.NEWCONTEXT
             || cce.getName() == ContextChangeEvents.LOADEDFILE) {
                // Nouveau fichier : réinitialiser tout le tracking
                geometricParents.clear();
                geometricChildren.clear();
                uniformParents.clear();
                uniformChildren.clear();
                syncFromContext();
            } else if (cce.getName() == ContextChangeEvents.CONTEXTCHANGED) {
                syncFromContext();
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Add / Remove
    // ═════════════════════════════════════════════════════════════════════════

    private void addAttribute() {
        String name = JOptionPane.showInputDialog(this,
                "Nom du nouvel attribut :", "Ajouter Attribut", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        name = name.trim();
        for (int i = 0; i < state.context.getAttributeCount(); i++) {
            if (state.context.getAttributeAtIndex(i).equals(name)) {
                JOptionPane.showMessageDialog(this, "L'attribut '" + name + "' existe déjà.",
                        "Doublon", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        state.context.addAttribute(name);
        syncFromContext();
        state.contextChanged();
    }

    private void removeAttribute() {
        int col = matrixTable.getSelectedColumn();
        if (col <= 0) {
            JOptionPane.showMessageDialog(this, "Sélectionnez une colonne d'attribut.",
                    "Aucune sélection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String name = matrixTable.getColumnName(col);
        if (JOptionPane.showConfirmDialog(this, "Supprimer l'attribut '" + name + "' ?",
                "Confirmer", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        state.context.removeAttribute(name);
        syncFromContext();
        state.contextChanged();
    }

    private void addObject() {
        String name = JOptionPane.showInputDialog(this,
                "Nom du nouvel objet :", "Ajouter Objet", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        name = name.trim();
        for (int i = 0; i < state.context.getObjectCount(); i++) {
            if (state.context.getObjectAtIndex(i).getIdentifier().equals(name)) {
                JOptionPane.showMessageDialog(this, "L'objet '" + name + "' existe déjà.",
                        "Doublon", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        try {
            state.context.addObject(new de.tudresden.inf.tcs.fcalib.FullObject(name));
        } catch (de.tudresden.inf.tcs.fcaapi.exception.IllegalObjectException ex) {
            JOptionPane.showMessageDialog(this, "Échec : " + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }
        syncFromContext();
        state.contextChanged();
    }

    private void removeObject() {
        int row = matrixTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Sélectionnez une ligne d'objet.",
                    "Aucune sélection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String name = (String) matrixModel.getValueAt(row, 0);
        if (JOptionPane.showConfirmDialog(this, "Supprimer l'objet '" + name + "' ?",
                "Confirmer", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        try {
            for (int i = 0; i < state.context.getObjectCount(); i++) {
                if (state.context.getObjectAtIndex(i).getIdentifier().equals(name)) {
                    state.context.removeObject(state.context.getObjectAtIndex(i));
                    break;
                }
            }
        } catch (de.tudresden.inf.tcs.fcaapi.exception.IllegalObjectException ex) {
            JOptionPane.showMessageDialog(this, "Échec : " + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }
        syncFromContext();
        state.contextChanged();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Import / Export Excel
    // ═════════════════════════════════════════════════════════════════════════

    private void importExcelFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Importer un fichier Excel");
        fc.setFileFilter(new FileNameExtensionFilter("Fichiers Excel (.xlsx, .xls)", "xlsx", "xls"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        ExcelData data;
        try { data = ExcelImporter.importFile(file); }
        catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Échec de l'import :\n" + ex.getMessage(),
                    "Erreur Import", JOptionPane.ERROR_MESSAGE);
            return;
        }
        List<String> newAttrs = new ArrayList<String>(), newObjs = new ArrayList<String>();
        boolean ok = ImportReconciliationDialog.showDialog(
                (JFrame) SwingUtilities.getWindowAncestor(this), data, state, newAttrs, newObjs);
        if (!ok) return;
        for (String a : newAttrs) state.context.addAttribute(a);
        try {
            for (String o : newObjs)
                state.context.addObject(new de.tudresden.inf.tcs.fcalib.FullObject(o));
        } catch (de.tudresden.inf.tcs.fcaapi.exception.IllegalObjectException ex) {
            JOptionPane.showMessageDialog(this, "Échec :\n" + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }
        syncFromContext();
        for (String obj : data.objects) {
            Map<String, Double> ov = continuousValues.get(obj);
            if (ov == null) { ov = new LinkedHashMap<String, Double>(); continuousValues.put(obj, ov); }
            Map<String, Double> ev = data.values.get(obj);
            if (ev != null) ov.putAll(ev);
        }
        matrixModel.refresh();
        state.contextChanged();
        JOptionPane.showMessageDialog(this,
                "Import réussi !\nAttributs : " + data.attributes.size() +
                "\nObjets : " + data.objects.size() + "\nValeurs : " + data.validCells,
                "Import Terminé", JOptionPane.INFORMATION_MESSAGE);
    }

    private void exportExcelFile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Fichiers Excel (*.xlsx)", "xlsx"));
        fc.setDialogTitle("Exporter vers Excel");
        fc.setSelectedFile(new File("raw_data_export.xlsx"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".xlsx"))
            file = new File(file.getAbsolutePath() + ".xlsx");
        try {
            org.apache.poi.xssf.usermodel.XSSFWorkbook wb =
                    new org.apache.poi.xssf.usermodel.XSSFWorkbook();
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Raw Data");
            org.apache.poi.ss.usermodel.Row   hr    = sheet.createRow(0);
            hr.createCell(0).setCellValue("");
            for (int i = 0; i < matrixModel.attrList.size(); i++)
                hr.createCell(i+1).setCellValue(matrixModel.attrList.get(i));
            for (int i = 0; i < matrixModel.objList.size(); i++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(i+1);
                String obj = matrixModel.objList.get(i);
                row.createCell(0).setCellValue(obj);
                Map<String, Double> ov = continuousValues.get(obj);
                if (ov != null)
                    for (int j = 0; j < matrixModel.attrList.size(); j++) {
                        Double v = ov.get(matrixModel.attrList.get(j));
                        if (v != null) row.createCell(j+1).setCellValue(v);
                    }
            }
            for (int i = 0; i <= matrixModel.attrList.size(); i++) sheet.autoSizeColumn(i);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            wb.write(fos); fos.close(); wb.close();
            JOptionPane.showMessageDialog(this,
                    "Export réussi !\n" + file.getAbsolutePath(),
                    "Export Terminé", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Échec :\n" + ex.getMessage(),
                    "Erreur Export", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Copy / Paste
    // ═════════════════════════════════════════════════════════════════════════

    private void copySelection() {
        int[] rows = matrixTable.getSelectedRows();
        int[] cols = matrixTable.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                Object v = matrixTable.getValueAt(rows[i], cols[j]);
                sb.append(v != null ? v : "");
                if (j < cols.length-1) sb.append("\t");
            }
            if (i < rows.length-1) sb.append("\n");
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
               .setContents(new StringSelection(sb.toString()), null);
    }

    private void pasteSelection() {
        int startRow = matrixTable.getSelectedRow();
        int startCol = matrixTable.getSelectedColumn();
        if (startRow < 0 || startCol < 0) return;
        try {
            String data = (String) Toolkit.getDefaultToolkit()
                    .getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (data == null || data.isEmpty()) return;
            String[] rows = data.split("\n");
            for (int i = 0; i < rows.length; i++) {
                int tr = startRow+i;
                if (tr >= matrixTable.getRowCount()) break;
                String[] cols = rows[i].split("\t");
                for (int j = 0; j < cols.length; j++) {
                    int tc = startCol+j;
                    if (tc >= matrixTable.getColumnCount() || tc == 0) continue;
                    matrixTable.setValueAt(cols[j].trim(), tr, tc);
                }
            }
            matrixTable.repaint();
        } catch (Exception ignored) {}
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Auto-refresh Min/Max pour GEOMETRIC et UNIFORME
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Recalcule et met à jour automatiquement Min/Max dans les configs
     * de tous les attributs en mode GEOMETRIC ou UNIFORME,
     * en lisant les données actuelles de la matrice continue.
     *
     * Appelé à chaque modification de la matrice (saisie utilisateur)
     * et à la synchronisation du contexte.
     */
    private void refreshAutoMinMax() {
        boolean changed = false;
        for (String attr : rawDataConfigs.keySet()) {
            RawDataConfig cfg = rawDataConfigs.get(attr);
            if (cfg == null) continue;
            if (cfg.getMode() == Mode.GEOMETRIC || cfg.getMode() == Mode.UNIFORME) {
                double[] mm = computeMinMaxForAttr(attr);
                if (mm != null) {
                    cfg.setMin(mm[0]);
                    cfg.setMax(mm[1]);
                    changed = true;
                }
            }
        }
        if (changed) {
            configModel.fireTableDataChanged();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Utilitaire : min/max auto-détecté dans la matrice continue
    // ═════════════════════════════════════════════════════════════════════════

    private double[] computeMinMaxForAttr(String attr) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        boolean hasValues = false;
        for (Map<String, Double> objValues : continuousValues.values()) {
            Double v = objValues.get(attr);
            if (v != null) {
                if (v < min) min = v;
                if (v > max) max = v;
                hasValues = true;
            }
        }
        if (!hasValues || min >= max) return null;
        return new double[]{ min, max };
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Table Models
    // ═════════════════════════════════════════════════════════════════════════

    private class ConfigTableModel extends AbstractTableModel {
        private final String[] COLS = { "Attribute", "Modalite", "Min", "Max", "Threshold / n" };
        List<String> attrList = new ArrayList<String>();

        void refresh() {
            attrList = new ArrayList<String>(rawDataConfigs.keySet());
            fireTableDataChanged();
        }

        public int    getRowCount()          { return attrList.size(); }
        public int    getColumnCount()       { return COLS.length; }
        public String getColumnName(int col) { return COLS[col]; }

        public boolean isCellEditable(int row, int col) {
            if (col == 0) return false;
            if (col == 1) return false; // géré par setupModeClickListener()
            String attr = attrList.get(row);
            RawDataConfig cfg = rawDataConfigs.get(attr);
            if (cfg == null) return false;
            if (col == 2) return cfg.isMinEditable();
            if (col == 3) return cfg.isMaxEditable();
            if (col == 4) return cfg.isThresholdEditable()
                    || cfg.getMode() == Mode.GEOMETRIC
                    || cfg.getMode() == Mode.UNIFORME;
            return false;
        }

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
                case 4: return (cfg.getMode() == Mode.GEOMETRIC || cfg.getMode() == Mode.UNIFORME)
                        ? cfg.getNumberOfClasses() : cfg.getThreshold();
            }
            return null;
        }

        public void setValueAt(Object value, int row, int col) {
            if (col == 0 || col == 1 || value == null) return;
            String attr = attrList.get(row);
            RawDataConfig cfg = rawDataConfigs.get(attr);
            if (cfg == null) return;
            if (col == 4 && (cfg.getMode() == Mode.GEOMETRIC || cfg.getMode() == Mode.UNIFORME)) {
                // spinner gère la mise à jour directement
                fireTableCellUpdated(row, col);
                return;
            }
            try {
                double d = Double.parseDouble(value.toString().trim().replace(",", "."));
                switch (col) {
                    case 2: cfg.setMin(d);       break;
                    case 3: cfg.setMax(d);       break;
                    case 4: cfg.setThreshold(d); break;
                }
                fireTableCellUpdated(row, col);
                matrixModel.fireTableDataChanged();
            } catch (NumberFormatException ignored) {}
        }
    }

    private class ConfigCellRenderer extends DefaultTableCellRenderer {
        public Component getTableCellRendererComponent(
                JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            setHorizontalAlignment(col == 0 ? SwingConstants.LEFT : SwingConstants.CENTER);
            if (col == 0) {
                setBackground(COLOR_HEADER);
                setFont(getFont().deriveFont(Font.BOLD));
            } else if (!t.isCellEditable(row, col)) {
                setBackground(COLOR_DISABLED);
                setForeground(Color.GRAY);
            } else if (col == 4) {
                setBackground(new Color(255, 255, 220));
                setForeground(Color.BLACK);
                setFont(getFont().deriveFont(Font.BOLD));
            } else {
                setBackground(Color.WHITE);
                setForeground(Color.BLACK);
            }
            return this;
        }
    }

    private class MatrixTableModel extends AbstractTableModel {
        List<String> attrList = new ArrayList<String>();
        List<String> objList  = new ArrayList<String>();

        void refresh() {
            attrList = new ArrayList<String>(rawDataConfigs.keySet());
            objList  = new ArrayList<String>(continuousValues.keySet());
            fireTableStructureChanged();
        }

        public int    getRowCount()    { return objList.size(); }
        public int    getColumnCount() { return attrList.size() + 1; }
        public String getColumnName(int col) { return col == 0 ? "Object" : attrList.get(col-1); }
        public boolean isCellEditable(int row, int col) { return col != 0; }

        public Object getValueAt(int row, int col) {
            if (col == 0) return objList.get(row);
            String obj  = objList.get(row);
            String attr = attrList.get(col-1);
            Map<String, Double> vals = continuousValues.get(obj);
            return (vals != null) ? vals.get(attr) : null;
        }

        public void setValueAt(Object value, int row, int col) {
            if (col == 0) return;
            String obj  = objList.get(row);
            String attr = attrList.get(col-1);
            Map<String, Double> vals = continuousValues.get(obj);
            if (vals == null) {
                vals = new LinkedHashMap<String, Double>();
                continuousValues.put(obj, vals);
            }
            String text = (value == null) ? "" : value.toString().trim();
            if (text.isEmpty()) vals.remove(attr);
            else {
                try { vals.put(attr, Double.parseDouble(text.replace(",", "."))); }
                catch (NumberFormatException ignored) {}
            }
            fireTableCellUpdated(row, col);
            // Mise à jour en temps réel : recalcul Min/Max auto + couleurs de la matrice
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    refreshAutoMinMax();
                    matrixModel.fireTableDataChanged();
                }
            });
        }
    }

    private class MatrixCellRenderer extends DefaultTableCellRenderer {
        public Component getTableCellRendererComponent(
                JTable t, Object value, boolean sel, boolean foc, int row, int col) {
            String display = col == 0
                    ? (value != null ? value.toString() : "")
                    : (value instanceof Double ? String.valueOf(value) : "");
            super.getTableCellRendererComponent(t, display, sel, foc, row, col);
            setHorizontalAlignment(col == 0 ? SwingConstants.LEFT : SwingConstants.CENTER);

            if (isDragging && ((col == 0 && row == draggedRow) || col == draggedCol)) {
                setBackground(COLOR_DRAG_HIGHLIGHT);
                setForeground(Color.BLACK);
            } else if (sel) {
                setBackground(new Color(184, 207, 229));
                setForeground(Color.BLACK);
            } else if (col == 0) {
                setBackground(COLOR_HEADER);
                setFont(getFont().deriveFont(Font.BOLD));
            } else if (value == null) {
                setBackground(COLOR_EMPTY);
                setFont(getFont().deriveFont(Font.PLAIN));
            } else {
                String        attr = matrixModel.attrList.get(col-1);
                RawDataConfig cfg  = rawDataConfigs.get(attr);
                if (cfg != null) {
                    if (cfg.getMode() == Mode.GEOMETRIC) {
                        String err = cfg.validateGeometric();
                        if (err == null) {
                            int cls = GeometricScale.classifyValue(
                                    (Double) value, cfg.getMin(), cfg.getMax(), cfg.getNumberOfClasses());
                            if (cls > 0) {
                                setBackground(new Color(200 + (cls*10)%55, 220, 255));
                                setText("C" + cls + " (" + display + ")");
                            } else {
                                setBackground(new Color(255, 240, 200));
                                setText("! " + display);
                            }
                        } else {
                            setBackground(COLOR_EMPTY);
                        }
                    } else if (cfg.getMode() == Mode.UNIFORME) {
                        String err = cfg.validateUniform();
                        if (err == null) {
                            int cls = UniformScale.classifyValue(
                                    (Double) value, cfg.getMin(), cfg.getMax(), cfg.getNumberOfClasses());
                            if (cls > 0) {
                                // Teinte violacée pour distinguer de GEOMETRIC (bleu)
                                setBackground(new Color(220 + (cls*8)%35, 200, 255));
                                setText("U" + cls + " (" + display + ")");
                            } else {
                                setBackground(new Color(255, 240, 200));
                                setText("! " + display);
                            }
                        } else {
                            setBackground(COLOR_EMPTY);
                        }
                    } else {
                        setBackground(cfg.shouldBeChecked((Double) value)
                                ? COLOR_ABOVE : COLOR_BELOW);
                    }
                } else {
                    setBackground(COLOR_EMPTY);
                }
                setFont(getFont().deriveFont(Font.PLAIN));
            }
            return this;
        }
    }

    private class DoubleCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JTextField field = new JTextField();
        DoubleCellEditor() {
            field.setHorizontalAlignment(JTextField.CENTER);
            field.setBorder(BorderFactory.createLineBorder(COLOR_APPLY_BG, 2));
        }
        public boolean isCellEditable(EventObject e) {
            return !(e instanceof MouseEvent) || ((MouseEvent) e).getClickCount() >= 2;
        }
        public Object getCellEditorValue() { return field.getText().trim(); }
        public Component getTableCellEditorComponent(
                JTable t, Object value, boolean sel, int row, int col) {
            field.setText(value instanceof Double ? String.valueOf(value) : "");
            field.selectAll();
            return field;
        }
    }
}