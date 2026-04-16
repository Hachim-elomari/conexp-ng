package fcatools.conexpng.gui.lattice;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import com.alee.extended.panel.WebAccordion;
import com.alee.laf.button.WebButton;
import com.alee.laf.checkbox.WebCheckBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.radiobutton.WebRadioButton;
import com.alee.laf.scroll.WebScrollPane;

import de.tudresden.inf.tcs.fcalib.FullObject;
import fcatools.conexpng.Conf;
import fcatools.conexpng.io.locale.LocaleHandler;
import fcatools.conexpng.model.FormalContext;

/**
 * This class implements the accordion menu of the lattice tab.
 * 
 * (F1) FINAL : 
 * - Afficher attributs orphelins comme groupes fictifs TEMPORAIRES (pas de modification du contexte)
 * - Mode WITH/WITHOUT Groups
 * - RESPONSIVE (centré)
 * - Bouton Switch dynamique (Switch to Groups / Switch to Attributes)
 */
public class LatticeSettings extends WebAccordion {

    private static final long serialVersionUID = 3981827958628799515L;
    private Conf state;
    private FormalContext context;
    private List<WebCheckBox> attributeCheckBoxes;
    private List<WebCheckBox> objectCheckBoxes;
    
    private boolean withGroups = true;
    private WebButton switchModeButton;

    public LatticeSettings(Conf state) {
        this.state = state;
        this.context = state.context;
        this.attributeCheckBoxes = new ArrayList<>();
        this.objectCheckBoxes = new ArrayList<>();
        
        this.addPane(0, LocaleHandler.getString("LatticeSettings.LatticeSettings.pane.0"), getLatticePanel());
        this.addPane(1, LocaleHandler.getString("LatticeSettings.LatticeSettings.pane.1"), getObjectPanel());
        this.addPane(2, LocaleHandler.getString("LatticeSettings.LatticeSettings.pane.2"), getAttributePanel());
    }

    private WebScrollPane getLatticePanel() {
        WebPanel panel = new WebPanel(new BorderLayout());
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;

        panel.add(getLatticeObjectPanel(), gbc);

        gbc.gridx = 1;

        panel.add(getLatticeAttrPanel(), gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        panel.add(new WebLabel(LocaleHandler.getString("LatticeSettings.getLatticePanel.WebLabel.1")), gbc);
        final WebRadioButton noneEdges = new WebRadioButton(
                LocaleHandler.getString("LatticeSettings.getLatticePanel.noneEdges"));
        gbc.gridy++;
        final WebRadioButton showEdges = new WebRadioButton(
                LocaleHandler.getString("LatticeSettings.getLatticePanel.showEdges"));
        if (state.guiConf.showEdges) {
            showEdges.setSelected(true);
        } else {
            noneEdges.setSelected(true);
        }
        noneEdges.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                state.guiConf.showEdges = false;
                noneEdges.setSelected(true);
                showEdges.setSelected(false);
                state.showLabelsChanged();
            }
        });
        showEdges.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                state.guiConf.showEdges = true;
                showEdges.setSelected(true);
                noneEdges.setSelected(false);
                state.showLabelsChanged();
            }
        });

        panel.add(noneEdges, gbc);
        gbc.gridx = 1;
        panel.add(showEdges, gbc);

        return new WebScrollPane(panel);
    }

    private WebPanel getLatticeObjectPanel() {
        WebPanel panelObjects = new WebPanel(new BorderLayout());
        panelObjects.setLayout(new GridBagLayout());
        GridBagConstraints gbo = new GridBagConstraints();
        gbo.anchor = GridBagConstraints.WEST;
        gbo.gridx = 0;
        gbo.gridy = 1;
        panelObjects
                .add(new WebLabel(LocaleHandler.getString("LatticeSettings.getLatticeObjectPanel.WebLabel.1")), gbo);
        gbo.gridy = 2;
        final WebRadioButton noneObjects = new WebRadioButton();
        noneObjects.setText(LocaleHandler.getString("LatticeSettings.getLatticeObjectPanel.noneObjects"));

        final WebRadioButton labelsObjects = new WebRadioButton();
        labelsObjects.setText(LocaleHandler.getString("LatticeSettings.getLatticeObjectPanel.labelsObjects"));

        if (state.guiConf.showObjectLabel) {
            labelsObjects.setSelected(true);
        } else {
            noneObjects.setSelected(true);
        }

        noneObjects.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                state.guiConf.showObjectLabel = false;
                noneObjects.setSelected(true);
                labelsObjects.setSelected(false);
                state.showLabelsChanged();
            }
        });
        labelsObjects.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                state.guiConf.showObjectLabel = true;
                labelsObjects.setSelected(true);
                noneObjects.setSelected(false);
                state.showLabelsChanged();
            }
        });
        panelObjects.add(noneObjects, gbo);
        gbo.gridy = 3;
        panelObjects.add(labelsObjects, gbo);
        return panelObjects;
    }

    private WebPanel getLatticeAttrPanel() {
        WebPanel panelAttributes = new WebPanel(new BorderLayout());
        panelAttributes.setLayout(new GridBagLayout());
        GridBagConstraints gba = new GridBagConstraints();
        gba.anchor = GridBagConstraints.WEST;
        gba.gridx = 0;
        gba.gridy = 1;
        panelAttributes.add(new WebLabel(LocaleHandler.getString("LatticeSettings.getLatticeAttrPanel.WebLabel.1")),
                gba);
        gba.gridy = 2;
        final WebRadioButton noneAttributes = new WebRadioButton();
        noneAttributes.setText(LocaleHandler.getString("LatticeSettings.getLatticeAttrPanel.noneAttributes"));
        final WebRadioButton labelsAttributes = new WebRadioButton();
        labelsAttributes.setText(LocaleHandler.getString("LatticeSettings.getLatticeAttrPanel.labelsAttributes"));

        if (state.guiConf.showAttributeLabel) {
            labelsAttributes.setSelected(true);
        } else {
            noneAttributes.setSelected(true);
        }

        noneAttributes.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                state.guiConf.showAttributeLabel = false;
                noneAttributes.setSelected(true);
                labelsAttributes.setSelected(false);
                state.showLabelsChanged();
            }
        });
        labelsAttributes.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                state.guiConf.showAttributeLabel = true;
                labelsAttributes.setSelected(true);
                noneAttributes.setSelected(false);
                state.showLabelsChanged();
            }
        });
        panelAttributes.add(noneAttributes, gba);
        gba.gridy = 3;
        panelAttributes.add(labelsAttributes, gba);
        return panelAttributes;
    }

    /**
     * (F1) FINAL : 
     * - Afficher les groupes réels + les attributs orphelins comme groupes FICTIFS
     * - Ne JAMAIS modifier le contexte (temporaire uniquement)
     * - Mode WITH/WITHOUT Groups avec switch dynamique
     * - RESPONSIVE (centré avec margins)
     */
    private WebScrollPane getAttributePanel() {
        WebPanel mainPanel = new WebPanel(new BorderLayout(5, 5));
        mainPanel.setMargin(10, 10, 10, 10);  // Margin global pour responsive
        
        // ─────────────────────────────────────────────────────────────────────
        // SECTION 1 : BOUTONS (RESPONSIVE)
        // ─────────────────────────────────────────────────────────────────────
        WebPanel buttonsPanel = new WebPanel(new GridLayout(2, 2, 3, 3));
        buttonsPanel.setMargin(5, 5, 5, 5);
        
        // Bouton Switch DYNAMIQUE
        switchModeButton = new WebButton(withGroups ? "Switch to Attributes" : "Switch to Groups");
        switchModeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                withGroups = !withGroups;
                System.out.println("[F1-LATTICESETTINGS] Mode toggle -> " + (withGroups ? "WITH" : "WITHOUT") + " Groups");
                update(state);
            }
        });
        buttonsPanel.add(switchModeButton);
        
        WebButton showAllButton = new WebButton("Show All");
        showAllButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("[F1-LATTICESETTINGS] Show All");
                state.context.clearConsidered();
                state.temporaryContextChanged();
                update(state);
            }
        });
        buttonsPanel.add(showAllButton);
        
        WebButton hideAllButton = new WebButton("Hide All");
        hideAllButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("[F1-LATTICESETTINGS] Hide All");
                for (int i = 0; i < context.getAttributeCount(); i++) {
                    String attr = context.getAttributeAtIndex(i);
                    state.context.dontConsiderAttribute(attr);
                }
                state.temporaryContextChanged();
                update(state);
            }
        });
        buttonsPanel.add(hideAllButton);
        
        buttonsPanel.add(new WebPanel());
        
        mainPanel.add(buttonsPanel, BorderLayout.NORTH);
        
        // ─────────────────────────────────────────────────────────────────────
        // SECTION 2 : PANEL AVEC ATTRIBUTS (RESPONSIVE ET CENTRÉ)
        // ─────────────────────────────────────────────────────────────────────
        WebPanel contentPanel = new WebPanel(new GridBagLayout());
        contentPanel.setMargin(10, 15, 10, 15);  // Margin pour centrage responsive
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new java.awt.Insets(2, 5, 2, 5);
        
        GridBagConstraints gbcAttr = new GridBagConstraints();
        gbcAttr.anchor = GridBagConstraints.WEST;
        gbcAttr.gridx = 0;
        gbcAttr.fill = GridBagConstraints.HORIZONTAL;
        gbcAttr.weightx = 1.0;
        gbcAttr.insets = new java.awt.Insets(2, 30, 2, 5);
        
        if (withGroups) {
            // MODE WITH GROUPS
            System.out.println("[F1-LATTICESETTINGS] Affichage MODE WITH GROUPS");
            
            java.util.Collection<fcatools.conexpng.model.AttributeGroup> allGroups = 
                context.getAllAttributeGroups();
            
            if (allGroups != null && !allGroups.isEmpty()) {
                for (final fcatools.conexpng.model.AttributeGroup group : allGroups) {
                    gbc.gridy++;
                    
                    final WebCheckBox groupCheckbox = new WebCheckBox(group.getGroupName());
                    
                    // RECALCUL DYNAMIQUE : Un groupe est coché SEULEMENT si TOUS ses attributs sont cochés
                    boolean allAttributesChecked = true;
                    for (String attr : group.getAttributeNames()) {
                        if (state.context.getDontConsideredAttr().contains(attr)) {
                            allAttributesChecked = false;
                            break;
                        }
                    }
                    groupCheckbox.setSelected(allAttributesChecked);
                    
                    groupCheckbox.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            System.out.println("[F1-LATTICESETTINGS] Groupe '" + group.getGroupName() + 
                                             "' -> " + groupCheckbox.isSelected());
                            
                            // Cocher/décocher le groupe = cocher/décocher TOUS ses attributs
                            if (!groupCheckbox.isSelected()) {
                                for (String attr : group.getAttributeNames()) {
                                    state.context.dontConsiderAttribute(attr);
                                }
                            } else {
                                for (String attr : group.getAttributeNames()) {
                                    state.context.considerAttribute(attr);
                                }
                            }
                            state.temporaryContextChanged();
                        }
                    });
                    
                    contentPanel.add(groupCheckbox, gbc);
                    
                    for (final String attrName : group.getAttributeNames()) {
                        gbc.gridy++;
                        gbcAttr.gridy = gbc.gridy;
                        final WebCheckBox attrCheckbox = new WebCheckBox("  └─ " + attrName);
                        
                        attrCheckbox.setEnabled(false);
                        attrCheckbox.setForeground(Color.GRAY);
                        attrCheckbox.setSelected(!state.context.getDontConsideredAttr().contains(attrName));
                        
                        contentPanel.add(attrCheckbox, gbcAttr);
                        attributeCheckBoxes.add(attrCheckbox);
                    }
                }
            }
            
            // AFFICHER LES ATTRIBUTS ORPHELINS comme groupes fictifs EN MAJUSCULES (TEMPORAIRES)
            java.util.Collection<String> ungroupedAttrs = context.getUngroupedAttributes();
            if (ungroupedAttrs != null && !ungroupedAttrs.isEmpty()) {
                System.out.println("[F1-LATTICESETTINGS] Affichage de " + ungroupedAttrs.size() + " attributs orphelins comme groupes fictifs");
                
                for (final String attrName : ungroupedAttrs) {
                    gbc.gridy++;
                    
                    final WebCheckBox fictiveGroupCheckbox = new WebCheckBox(attrName.toUpperCase());
                    fictiveGroupCheckbox.setSelected(!state.context.getDontConsideredAttr().contains(attrName));
                    
                    fictiveGroupCheckbox.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            if (!fictiveGroupCheckbox.isSelected()) {
                                state.context.dontConsiderAttribute(attrName);
                            } else {
                                state.context.considerAttribute(attrName);
                            }
                            state.temporaryContextChanged();
                        }
                    });
                    
                    contentPanel.add(fictiveGroupCheckbox, gbc);
                    
                    gbc.gridy++;
                    gbcAttr.gridy = gbc.gridy;
                    final WebCheckBox orphanAttrCheckbox = new WebCheckBox("  └─ " + attrName);
                    orphanAttrCheckbox.setEnabled(false);
                    orphanAttrCheckbox.setForeground(Color.GRAY);
                    orphanAttrCheckbox.setSelected(!state.context.getDontConsideredAttr().contains(attrName));
                    
                    contentPanel.add(orphanAttrCheckbox, gbcAttr);
                    attributeCheckBoxes.add(orphanAttrCheckbox);
                }
            }
        } else {
            // MODE WITHOUT GROUPS
            System.out.println("[F1-LATTICESETTINGS] Affichage MODE WITHOUT GROUPS");
            
            java.util.Collection<fcatools.conexpng.model.AttributeGroup> allGroups = 
                context.getAllAttributeGroups();
            
            if (allGroups != null && !allGroups.isEmpty()) {
                for (final fcatools.conexpng.model.AttributeGroup group : allGroups) {
                    gbc.gridy++;
                    final WebCheckBox groupCheckbox = new WebCheckBox(group.getGroupName());
                    
                    groupCheckbox.setEnabled(false);
                    groupCheckbox.setForeground(Color.GRAY);
                    
                    // RECALCUL DYNAMIQUE : Afficher l'état réel du groupe
                    boolean allAttributesChecked = true;
                    for (String attr : group.getAttributeNames()) {
                        if (state.context.getDontConsideredAttr().contains(attr)) {
                            allAttributesChecked = false;
                            break;
                        }
                    }
                    groupCheckbox.setSelected(allAttributesChecked);
                    
                    contentPanel.add(groupCheckbox, gbc);
                    
                    for (final String attrName : group.getAttributeNames()) {
                        gbc.gridy++;
                        gbcAttr.gridy = gbc.gridy;
                        final WebCheckBox attrCheckbox = new WebCheckBox("  └─ " + attrName);
                        
                        attrCheckbox.setSelected(!state.context.getDontConsideredAttr().contains(attrName));
                        
                        attrCheckbox.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent arg0) {
                                if (!attrCheckbox.isSelected()) {
                                    state.context.dontConsiderAttribute(attrName);
                                } else {
                                    state.context.considerAttribute(attrName);
                                }
                                state.temporaryContextChanged();
                            }
                        });
                        
                        contentPanel.add(attrCheckbox, gbcAttr);
                        attributeCheckBoxes.add(attrCheckbox);
                    }
                }
            }
            
            // AFFICHER LES ATTRIBUTS ORPHELINS comme groupes fictifs EN MAJUSCULES (GRIS, non-cochables)
            java.util.Collection<String> ungroupedAttrs = context.getUngroupedAttributes();
            if (ungroupedAttrs != null && !ungroupedAttrs.isEmpty()) {
                System.out.println("[F1-LATTICESETTINGS] Affichage de " + ungroupedAttrs.size() + " attributs orphelins (groupe fictif gris)");
                
                for (final String attrName : ungroupedAttrs) {
                    gbc.gridy++;
                    
                    final WebCheckBox fictiveGroupCheckbox = new WebCheckBox(attrName.toUpperCase());
                    fictiveGroupCheckbox.setEnabled(false);
                    fictiveGroupCheckbox.setForeground(Color.GRAY);
                    
                    boolean isChecked = !state.context.getDontConsideredAttr().contains(attrName);
                    fictiveGroupCheckbox.setSelected(isChecked);
                    
                    contentPanel.add(fictiveGroupCheckbox, gbc);
                    
                    gbc.gridy++;
                    gbcAttr.gridy = gbc.gridy;
                    final WebCheckBox orphanAttrCheckbox = new WebCheckBox("  └─ " + attrName);
                    orphanAttrCheckbox.setSelected(!state.context.getDontConsideredAttr().contains(attrName));
                    
                    orphanAttrCheckbox.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            if (!orphanAttrCheckbox.isSelected()) {
                                state.context.dontConsiderAttribute(attrName);
                            } else {
                                state.context.considerAttribute(attrName);
                            }
                            state.temporaryContextChanged();
                        }
                    });
                    
                    contentPanel.add(orphanAttrCheckbox, gbcAttr);
                    attributeCheckBoxes.add(orphanAttrCheckbox);
                }
            }
        }
        
        WebScrollPane contentScrollPane = new WebScrollPane(contentPanel);
        mainPanel.add(contentScrollPane, BorderLayout.CENTER);
        
        // Ajouter scrollbar au mainPanel ENTIER (pas juste contentPanel)
        WebScrollPane mainScrollPane = new WebScrollPane(mainPanel);
        return mainScrollPane;
    }

    private WebScrollPane getObjectPanel() {
        WebPanel panel = new WebPanel(new BorderLayout());
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        for (FullObject<String, String> s : context.getObjects()) {
            gbc.gridy++;
            final WebCheckBox box = new WebCheckBox(s.getIdentifier());
            final FullObject<String, String> temp = s;
            box.setSelected(!state.context.getDontConsideredObj().contains(temp));
            box.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent arg0) {
                    if (!box.isSelected()) {
                        state.context.dontConsiderObject(temp);
                    } else {
                        state.context.considerObject(temp);
                    }
                    state.temporaryContextChanged();
                }
            });

            panel.add(box, gbc);
            objectCheckBoxes.add(box);
        }
        WebScrollPane sp = new WebScrollPane(panel);
        return sp;
    }

    /**
     * Updates the panels and refreshes the Switch button label
     * 
     * @param state
     */
    public void update(Conf state) {
        this.removePane(0);
        this.addPane(0, LocaleHandler.getString("LatticeSettings.LatticeSettings.pane.0"), getLatticePanel());
        this.context = state.context;
        this.removePane(1);
        objectCheckBoxes.clear();
        this.addPane(1, LocaleHandler.getString("LatticeSettings.LatticeSettings.pane.1"), getObjectPanel());
        this.removePane(2);
        attributeCheckBoxes.clear();
        this.addPane(2, LocaleHandler.getString("LatticeSettings.LatticeSettings.pane.2"), getAttributePanel());
    }
}