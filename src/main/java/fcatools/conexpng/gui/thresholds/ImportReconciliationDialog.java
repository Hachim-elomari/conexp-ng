package fcatools.conexpng.gui.thresholds;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.*;

import fcatools.conexpng.Conf;

/**
 * Dialog that compares imported Excel data with the current context,
 * and lets the user choose which attributes/objects to add or remove.
 */
public class ImportReconciliationDialog extends JDialog {

    private static final Color COLOR_NEW     = new Color(200, 240, 200);
    private static final Color COLOR_MISSING = new Color(255, 230, 200);
    private static final Color COLOR_HEADER  = new Color(220, 220, 220);
    
    private boolean confirmed = false;
    
    private JCheckBox chkAddNewAttributes;
    private JCheckBox chkAddNewObjects;
    private JCheckBox chkRemoveMissingAttributes;
    private JCheckBox chkRemoveMissingObjects;
    
    private List<String> newAttributes;
    private List<String> newObjects;
    private List<String> missingAttributes;
    private List<String> missingObjects;
    
    /**
     * Show the reconciliation dialog and wait for user decision.
     * 
     * @return true if user clicked "Import", false if cancelled
     */
    public static boolean showDialog(JFrame parent, ExcelData excelData, Conf state,
                                      List<String> outNewAttrs, List<String> outNewObjs) {
        
        ImportReconciliationDialog dialog = new ImportReconciliationDialog(
                parent, excelData, state);
        dialog.setVisible(true);
        
        if (dialog.confirmed) {
            outNewAttrs.addAll(dialog.getSelectedNewAttributes());
            outNewObjs.addAll(dialog.getSelectedNewObjects());
        }
        
        return dialog.confirmed;
    }
    
    private ImportReconciliationDialog(JFrame parent, ExcelData excelData, Conf state) {
        super(parent, "Import Excel - Review Changes", true);
        setLayout(new BorderLayout(10, 10));
        
        // ── Analyze differences ───────────────────────────────────────────────
        analyzeDifferences(excelData, state);
        
        // ── Build UI ──────────────────────────────────────────────────────────
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Summary text
        JTextArea summaryArea = new JTextArea();
        summaryArea.setEditable(false);
        summaryArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        summaryArea.setBackground(COLOR_HEADER);
        summaryArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        summaryArea.setText(buildSummaryText(excelData));
        
        JScrollPane summaryScroll = new JScrollPane(summaryArea);
        summaryScroll.setPreferredSize(new Dimension(500, 150));
        summaryScroll.setBorder(BorderFactory.createTitledBorder("Import Summary"));
        
        // Options checkboxes
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setBorder(BorderFactory.createTitledBorder("Actions"));
        
        chkAddNewAttributes = new JCheckBox(
                "Add " + newAttributes.size() + " new attribute(s) to context", true);
        chkAddNewObjects = new JCheckBox(
                "Add " + newObjects.size() + " new object(s) to context", true);
        chkRemoveMissingAttributes = new JCheckBox(
                "Remove " + missingAttributes.size() + " attribute(s) not in Excel", false);
        chkRemoveMissingObjects = new JCheckBox(
                "Remove " + missingObjects.size() + " object(s) not in Excel", false);
        
        chkAddNewAttributes.setEnabled(newAttributes.size() > 0);
        chkAddNewObjects.setEnabled(newObjects.size() > 0);
        chkRemoveMissingAttributes.setEnabled(missingAttributes.size() > 0);
        chkRemoveMissingObjects.setEnabled(missingObjects.size() > 0);
        
        optionsPanel.add(chkAddNewAttributes);
        optionsPanel.add(Box.createVerticalStrut(5));
        optionsPanel.add(chkAddNewObjects);
        optionsPanel.add(Box.createVerticalStrut(5));
        optionsPanel.add(chkRemoveMissingAttributes);
        optionsPanel.add(Box.createVerticalStrut(5));
        optionsPanel.add(chkRemoveMissingObjects);
        
        mainPanel.add(summaryScroll, BorderLayout.CENTER);
        mainPanel.add(optionsPanel, BorderLayout.SOUTH);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                confirmed = false;
                dispose();
            }
        });
        
        JButton importButton = new JButton("Import");
        importButton.setBackground(new Color(60, 120, 180));
        importButton.setForeground(Color.BLACK);
        importButton.setFont(importButton.getFont().deriveFont(Font.BOLD));
        importButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                confirmed = true;
                dispose();
            }
        });
        
        buttonPanel.add(cancelButton);
        buttonPanel.add(importButton);
        
        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        
        pack();
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    }
    
    /**
     * Compare Excel data with current context and identify differences.
     */
    private void analyzeDifferences(ExcelData excelData, Conf state) {
        // Get current attributes and objects
        Set<String> contextAttrs = new HashSet<String>();
        for (int i = 0; i < state.context.getAttributeCount(); i++) {
            contextAttrs.add(state.context.getAttributeAtIndex(i));
        }
        
        Set<String> contextObjs = new HashSet<String>();
        for (int i = 0; i < state.context.getObjectCount(); i++) {
            contextObjs.add(state.context.getObjectAtIndex(i).getIdentifier());
        }
        
        // Find new attributes (in Excel but not in context)
        newAttributes = new ArrayList<String>();
        for (String attr : excelData.attributes) {
            if (!contextAttrs.contains(attr)) {
                newAttributes.add(attr);
            }
        }
        
        // Find new objects (in Excel but not in context)
        newObjects = new ArrayList<String>();
        for (String obj : excelData.objects) {
            if (!contextObjs.contains(obj)) {
                newObjects.add(obj);
            }
        }
        
        // Find missing attributes (in context but not in Excel)
        missingAttributes = new ArrayList<String>();
        for (String attr : contextAttrs) {
            if (!excelData.attributes.contains(attr)) {
                missingAttributes.add(attr);
            }
        }
        
        // Find missing objects (in context but not in Excel)
        missingObjects = new ArrayList<String>();
        for (String obj : contextObjs) {
            if (!excelData.objects.contains(obj)) {
                missingObjects.add(obj);
            }
        }
    }
    
    /**
     * Build summary text for display.
     */
    private String buildSummaryText(ExcelData data) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Attributes found: ").append(data.attributes.size()).append("\n");
        sb.append("  - Already in context: ").append(data.attributes.size() - newAttributes.size()).append("\n");
        if (newAttributes.size() > 0) {
            sb.append("  + New: ").append(newAttributes.size()).append(" → ");
            sb.append(abbreviate(newAttributes, 3)).append("\n");
        }
        if (missingAttributes.size() > 0) {
            sb.append("  - Missing from Excel: ").append(missingAttributes.size()).append(" → ");
            sb.append(abbreviate(missingAttributes, 3)).append("\n");
        }
        
        sb.append("\n");
        
        sb.append("Objects found: ").append(data.objects.size()).append("\n");
        sb.append("  - Already in context: ").append(data.objects.size() - newObjects.size()).append("\n");
        if (newObjects.size() > 0) {
            sb.append("  + New: ").append(newObjects.size()).append(" → ");
            sb.append(abbreviate(newObjects, 3)).append("\n");
        }
        if (missingObjects.size() > 0) {
            sb.append("  - Missing from Excel: ").append(missingObjects.size()).append(" → ");
            sb.append(abbreviate(missingObjects, 3)).append("\n");
        }
        
        sb.append("\n");
        
        sb.append("Data cells:\n");
        sb.append("  ✓ Valid numeric values: ").append(data.validCells).append("\n");
        if (data.invalidCells > 0) {
            sb.append("  ⚠ Invalid/empty cells: ").append(data.invalidCells).append(" (will be skipped)\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Abbreviate a list for display (e.g., [a, b, c, ...]).
     */
    private String abbreviate(List<String> list, int maxItems) {
        if (list.size() <= maxItems) {
            return list.toString();
        }
        List<String> sub = new ArrayList<String>(list.subList(0, maxItems));
        return sub.toString().replace("]", ", ...]");
    }
    
    /**
     * Get attributes selected for addition.
     */
    public List<String> getSelectedNewAttributes() {
        if (chkAddNewAttributes.isSelected()) {
            return new ArrayList<String>(newAttributes);
        }
        return new ArrayList<String>();
    }
    
    /**
     * Get objects selected for addition.
     */
    public List<String> getSelectedNewObjects() {
        if (chkAddNewObjects.isSelected()) {
            return new ArrayList<String>(newObjects);
        }
        return new ArrayList<String>();
    }
}