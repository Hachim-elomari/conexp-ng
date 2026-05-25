package fcatools.conexpng.gui.lattice;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import fcatools.conexpng.Conf;
import fcatools.conexpng.Conf.ContextChangeEvent;
import fcatools.conexpng.ContextChangeEvents;
import fcatools.conexpng.model.AttributeGroup;
import fcatools.conexpng.model.FormalContext;

/**
 * (F1) Panel pour afficher et gérer la sélection des attributs/groupes dans le lattice
 *
 * ✅ FIX : Implémente PropertyChangeListener pour se rafraîchir automatiquement
 *          quand le contexte change (LOADEDFILE, NEWCONTEXT, CONTEXTCHANGED)
 */
@SuppressWarnings("serial")
public class AttributeSelectionPanel extends JPanel implements PropertyChangeListener {

    private Conf state;
    private FormalContext context;
    private ILatticeProvider latticeProvider;

    private JButton switchModeButton;
    private JPanel attributesPanel;
    private JScrollPane scrollPane;

    private Map<String, JCheckBox> attributeCheckboxes         = new HashMap<String, JCheckBox>();
    private Map<String, JCheckBox> groupCheckboxes              = new HashMap<String, JCheckBox>();
    private Map<String, Map<String, JCheckBox>> groupedAttributeCheckboxes
            = new HashMap<String, Map<String, JCheckBox>>();

    private enum LatticeDisplayMode { WITH_GROUPS, WITHOUT_GROUPS }
    private LatticeDisplayMode currentMode = LatticeDisplayMode.WITH_GROUPS;

    public interface ILatticeProvider {
        void updateLattice(Set<String> selectedAttributes);
    }

    public AttributeSelectionPanel(Conf state, ILatticeProvider latticeProvider) {
        this.state           = state;
        this.context         = state.context;
        this.latticeProvider = latticeProvider;

        setLayout(new BorderLayout(5, 5));

        attributesPanel = new JPanel();
        attributesPanel.setLayout(new BoxLayout(attributesPanel, BoxLayout.Y_AXIS));

        scrollPane = new JScrollPane(attributesPanel);

        add(createButtonPanel(), BorderLayout.NORTH);
        add(scrollPane,          BorderLayout.CENTER);

        // ✅ FIX : S'abonner aux changements de contexte
        state.addPropertyChangeListener(this);

        refreshAttributesPanel();
    }

    // ✅ FIX : Écouter les événements de changement de contexte
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt instanceof ContextChangeEvent) {
            ContextChangeEvent cce = (ContextChangeEvent) evt;
            
            // Rafraîchir le panel quand le contexte change
            if (cce.getName() == ContextChangeEvents.LOADEDFILE ||
                cce.getName() == ContextChangeEvents.NEWCONTEXT ||
                cce.getName() == ContextChangeEvents.CONTEXTCHANGED) {
                
                System.out.println("[AttributeSelectionPanel] Context changed, refreshing panel");
                refreshAttributesPanel();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Boutons
    // -------------------------------------------------------------------------

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel();

        switchModeButton = new JButton("Switch to Groups");
        switchModeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                toggleMode();
            }
        });
        panel.add(switchModeButton);

        JButton showAllButton = new JButton("Show All");
        showAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                selectAllAttributes();
            }
        });
        panel.add(showAllButton);

        JButton hideAllButton = new JButton("Hide All");
        hideAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                deselectAllAttributes();
            }
        });
        panel.add(hideAllButton);

        return panel;
    }

    private void toggleMode() {
        if (currentMode == LatticeDisplayMode.WITH_GROUPS) {
            currentMode = LatticeDisplayMode.WITHOUT_GROUPS;
            switchModeButton.setText("Switch to Groups");
        } else {
            currentMode = LatticeDisplayMode.WITH_GROUPS;
            switchModeButton.setText("Switch to WITHOUT Groups");
        }
        refreshAttributesPanel();
    }

    // -------------------------------------------------------------------------
    // Rafraîchissement du panel
    // -------------------------------------------------------------------------

    /**
     * Reconstruit le panel des attributs.
     */
    private void refreshAttributesPanel() {
        // Recharger le contexte (peut avoir changé depuis la construction)
        this.context = state.context;

        attributesPanel.removeAll();
        attributeCheckboxes.clear();
        groupCheckboxes.clear();
        groupedAttributeCheckboxes.clear();

        boolean hasGroups = !context.getAllAttributeGroups().isEmpty();

        if (!hasGroups) {
            switchModeButton.setVisible(false);
            buildUngroupedView();
        } else {
            switchModeButton.setVisible(true);

            if (currentMode == LatticeDisplayMode.WITHOUT_GROUPS) {
                switchModeButton.setText("Switch to Groups");
                buildUngroupedView();
            } else {
                switchModeButton.setText("Switch to WITHOUT Groups");
                buildGroupedView();
            }
        }

        attributesPanel.revalidate();
        attributesPanel.repaint();
        
        // ✅ FIX : Forcer la sélection de tous les attributs au démarrage
        selectAllAttributesSilently();
        
        updateLattice();
    }

    // -------------------------------------------------------------------------
    // Constructions des deux vues
    // -------------------------------------------------------------------------

    /** Liste plate : un JCheckBox par attribut. */
    private void buildUngroupedView() {
        for (int i = 0; i < context.getAttributeCount(); i++) {
            final String attrName = context.getAttributeAtIndex(i);
            JCheckBox cb = new JCheckBox(attrName);
            cb.setSelected(true);
            cb.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) { updateLattice(); }
            });
            attributeCheckboxes.put(attrName, cb);
            attributesPanel.add(cb);
        }
    }

    /** Vue hiérarchique : groupes cochables → attributs grisés en dessous. */
    private void buildGroupedView() {
        // 1. Groupes et leurs attributs
        for (final AttributeGroup group : context.getAllAttributeGroups()) {
            JCheckBox groupCb = new JCheckBox(group.getGroupName());
            groupCb.setSelected(true);
            groupCb.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    setGroupAttributesEnabled(group, e.getStateChange() == ItemEvent.SELECTED);
                    updateLattice();
                }
            });
            groupCheckboxes.put(group.getGroupId(), groupCb);
            attributesPanel.add(groupCb);

            Map<String, JCheckBox> groupAttrMap = new HashMap<String, JCheckBox>();
            for (final String attrName : group.getAttributeNames()) {
                JCheckBox attrCb = new JCheckBox("  \u2514\u2500 " + attrName);
                attrCb.setEnabled(false);
                attrCb.setForeground(Color.GRAY);
                attrCb.setSelected(true);
                groupAttrMap.put(attrName, attrCb);
                attributesPanel.add(attrCb);
            }
            groupedAttributeCheckboxes.put(group.getGroupId(), groupAttrMap);
        }

        // 2. Attributs non groupés
        for (final String attrName : context.getUngroupedAttributes()) {
            JCheckBox cb = new JCheckBox(attrName.toUpperCase());
            cb.setSelected(true);
            cb.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) { updateLattice(); }
            });
            attributeCheckboxes.put(attrName, cb);
            attributesPanel.add(cb);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void setGroupAttributesEnabled(AttributeGroup group, boolean checked) {
        Map<String, JCheckBox> map = groupedAttributeCheckboxes.get(group.getGroupId());
        if (map != null) {
            for (JCheckBox cb : map.values()) cb.setSelected(checked);
        }
    }

    /**
     * ✅ FIX : Sélectionner tous les attributs SANS déclencher updateLattice()
     * (pour éviter les appels multiples lors de l'initialisation)
     */
    private void selectAllAttributesSilently() {
        for (JCheckBox cb : attributeCheckboxes.values()) cb.setSelected(true);
        for (JCheckBox cb : groupCheckboxes.values())    cb.setSelected(true);
        for (Map<String, JCheckBox> m : groupedAttributeCheckboxes.values())
            for (JCheckBox cb : m.values()) cb.setSelected(true);
    }

    private void selectAllAttributes() {
        selectAllAttributesSilently();
        updateLattice();
    }

    private void deselectAllAttributes() {
        for (JCheckBox cb : attributeCheckboxes.values()) cb.setSelected(false);
        for (JCheckBox cb : groupCheckboxes.values())    cb.setSelected(false);
        for (Map<String, JCheckBox> m : groupedAttributeCheckboxes.values())
            for (JCheckBox cb : m.values()) cb.setSelected(false);
        updateLattice();
    }

    private Set<String> getSelectedAttributes() {
        Set<String> selected = new HashSet<String>();

        boolean hasGroups = !context.getAllAttributeGroups().isEmpty();

        if (!hasGroups || currentMode == LatticeDisplayMode.WITHOUT_GROUPS) {
            for (Map.Entry<String, JCheckBox> entry : attributeCheckboxes.entrySet()) {
                if (entry.getValue().isSelected()) selected.add(entry.getKey());
            }
        } else {
            for (String groupId : groupCheckboxes.keySet()) {
                if (groupCheckboxes.get(groupId).isSelected()) {
                    Map<String, JCheckBox> map = groupedAttributeCheckboxes.get(groupId);
                    if (map != null) selected.addAll(map.keySet());
                }
            }
            for (String attrName : context.getUngroupedAttributes()) {
                JCheckBox cb = attributeCheckboxes.get(attrName);
                if (cb != null && cb.isSelected()) selected.add(attrName);
            }
        }
        return selected;
    }

    private void updateLattice() {
        Set<String> selectedAttributes = getSelectedAttributes();
        if (state != null)           state.setSelectedAttributesForLattice(selectedAttributes);
        if (latticeProvider != null) latticeProvider.updateLattice(selectedAttributes);
    }
}