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
            public void actionPerformed(ActionEvent e) {
                state.guiConf.showEdges = false;
                noneEdges.setSelected(true);
                showEdges.setSelected(false);
                state.showLabelsChanged();
            }
        });
        showEdges.addActionListener(new ActionListener() {
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
        panelObjects.add(new WebLabel(LocaleHandler.getString("LatticeSettings.getLatticeObjectPanel.WebLabel.1")), gbo);
        gbo.gridy = 2;
        final WebRadioButton noneObjects   = new WebRadioButton();
        noneObjects.setText(LocaleHandler.getString("LatticeSettings.getLatticeObjectPanel.noneObjects"));
        final WebRadioButton labelsObjects = new WebRadioButton();
        labelsObjects.setText(LocaleHandler.getString("LatticeSettings.getLatticeObjectPanel.labelsObjects"));
        if (state.guiConf.showObjectLabel) labelsObjects.setSelected(true);
        else noneObjects.setSelected(true);
        noneObjects.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                state.guiConf.showObjectLabel = false;
                noneObjects.setSelected(true); labelsObjects.setSelected(false);
                state.showLabelsChanged();
            }
        });
        labelsObjects.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                state.guiConf.showObjectLabel = true;
                labelsObjects.setSelected(true); noneObjects.setSelected(false);
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
        panelAttributes.add(new WebLabel(LocaleHandler.getString("LatticeSettings.getLatticeAttrPanel.WebLabel.1")), gba);
        gba.gridy = 2;
        final WebRadioButton noneAttributes   = new WebRadioButton();
        noneAttributes.setText(LocaleHandler.getString("LatticeSettings.getLatticeAttrPanel.noneAttributes"));
        final WebRadioButton labelsAttributes = new WebRadioButton();
        labelsAttributes.setText(LocaleHandler.getString("LatticeSettings.getLatticeAttrPanel.labelsAttributes"));
        if (state.guiConf.showAttributeLabel) labelsAttributes.setSelected(true);
        else noneAttributes.setSelected(true);
        noneAttributes.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                state.guiConf.showAttributeLabel = false;
                noneAttributes.setSelected(true); labelsAttributes.setSelected(false);
                state.showLabelsChanged();
            }
        });
        labelsAttributes.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                state.guiConf.showAttributeLabel = true;
                labelsAttributes.setSelected(true); noneAttributes.setSelected(false);
                state.showLabelsChanged();
            }
        });
        panelAttributes.add(noneAttributes, gba);
        gba.gridy = 3;
        panelAttributes.add(labelsAttributes, gba);
        return panelAttributes;
    }

    private WebScrollPane getAttributePanel() {
        // Même structure que getObjectPanel() : panel simple avec GridBagLayout
        // + boutons en haut dans un panel séparé
        WebPanel outerPanel = new WebPanel(new BorderLayout());

        boolean hasGroups = context.getAllAttributeGroups() != null
                            && !context.getAllAttributeGroups().isEmpty();

        // ── BOUTONS (même style que Objects) ──────────────────────────────
        WebPanel buttonsPanel = new WebPanel(new GridLayout(0, 2, 3, 3));

        switchModeButton = new WebButton(withGroups ? "Switch to Attributes" : "Switch to Groups");
        switchModeButton.setVisible(hasGroups);
        switchModeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                withGroups = !withGroups;
                update(state);
            }
        });
        if (hasGroups) buttonsPanel.add(switchModeButton);

        WebButton showAllButton = new WebButton("Show All");
        showAllButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                state.context.clearConsidered();
                state.temporaryContextChanged();
                update(state);
            }
        });
        buttonsPanel.add(showAllButton);

        WebButton hideAllButton = new WebButton("Hide All");
        hideAllButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                for (int i = 0; i < context.getAttributeCount(); i++) {
                    state.context.dontConsiderAttribute(context.getAttributeAtIndex(i));
                }
                state.temporaryContextChanged();
                update(state);
            }
        });
        buttonsPanel.add(hideAllButton);

        outerPanel.add(buttonsPanel, BorderLayout.NORTH);

        // ── CONTENU (même structure que getObjectPanel) ───────────────────
        WebPanel panel = new WebPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor  = GridBagConstraints.WEST;
        gbc.gridx   = 0;
        gbc.gridy   = 0;

        GridBagConstraints gbcSub = new GridBagConstraints();
        gbcSub.anchor = GridBagConstraints.WEST;
        gbcSub.gridx  = 0;
        gbcSub.insets = new java.awt.Insets(0, 20, 0, 0);

        if (!hasGroups) {
            // ── AUCUN GROUPE : liste plate (identique au panel Objets) ─────
            for (int i = 0; i < context.getAttributeCount(); i++) {
                final String attrName = context.getAttributeAtIndex(i);
                gbc.gridy++;
                final WebCheckBox cb = new WebCheckBox(attrName);
                cb.setSelected(!state.context.getDontConsideredAttr().contains(attrName));
                cb.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        if (!cb.isSelected()) state.context.dontConsiderAttribute(attrName);
                        else                  state.context.considerAttribute(attrName);
                        state.temporaryContextChanged();
                    }
                });
                panel.add(cb, gbc);
                attributeCheckBoxes.add(cb);
            }

        } else if (withGroups) {
            // ── GROUPES + MODE WITH GROUPS ─────────────────────────────────
            for (final fcatools.conexpng.model.AttributeGroup group : context.getAllAttributeGroups()) {
                gbc.gridy++;
                final WebCheckBox groupCb = new WebCheckBox(group.getGroupName());
                boolean allChecked = true;
                for (String a : group.getAttributeNames())
                    if (state.context.getDontConsideredAttr().contains(a)) { allChecked = false; break; }
                groupCb.setSelected(allChecked);
                groupCb.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        for (String a : group.getAttributeNames()) {
                            if (!groupCb.isSelected()) state.context.dontConsiderAttribute(a);
                            else                       state.context.considerAttribute(a);
                        }
                        state.temporaryContextChanged();
                    }
                });
                panel.add(groupCb, gbc);

                for (final String attrName : group.getAttributeNames()) {
                    gbc.gridy++;
                    gbcSub.gridy = gbc.gridy;
                    final WebCheckBox attrCb = new WebCheckBox("  \u2514\u2500 " + attrName);
                    attrCb.setEnabled(false);
                    attrCb.setForeground(Color.GRAY);
                    attrCb.setSelected(!state.context.getDontConsideredAttr().contains(attrName));
                    panel.add(attrCb, gbcSub);
                    attributeCheckBoxes.add(attrCb);
                }
            }
            for (final String attrName : context.getUngroupedAttributes()) {
                gbc.gridy++;
                final WebCheckBox fictCb = new WebCheckBox(attrName.toUpperCase());
                fictCb.setSelected(!state.context.getDontConsideredAttr().contains(attrName));
                fictCb.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        if (!fictCb.isSelected()) state.context.dontConsiderAttribute(attrName);
                        else                      state.context.considerAttribute(attrName);
                        state.temporaryContextChanged();
                    }
                });
                panel.add(fictCb, gbc);
                gbc.gridy++;
                gbcSub.gridy = gbc.gridy;
                final WebCheckBox orphCb = new WebCheckBox("  \u2514\u2500 " + attrName);
                orphCb.setEnabled(false);
                orphCb.setForeground(Color.GRAY);
                orphCb.setSelected(!state.context.getDontConsideredAttr().contains(attrName));
                panel.add(orphCb, gbcSub);
                attributeCheckBoxes.add(orphCb);
            }

        } else {
            // ── GROUPES + MODE WITHOUT GROUPS ──────────────────────────────
            for (final fcatools.conexpng.model.AttributeGroup group : context.getAllAttributeGroups()) {
                gbc.gridy++;
                final WebCheckBox groupCb = new WebCheckBox(group.getGroupName());
                groupCb.setEnabled(false);
                groupCb.setForeground(Color.GRAY);
                boolean allChecked = true;
                for (String a : group.getAttributeNames())
                    if (state.context.getDontConsideredAttr().contains(a)) { allChecked = false; break; }
                groupCb.setSelected(allChecked);
                panel.add(groupCb, gbc);

                for (final String attrName : group.getAttributeNames()) {
                    gbc.gridy++;
                    gbcSub.gridy = gbc.gridy;
                    final WebCheckBox attrCb = new WebCheckBox("  \u2514\u2500 " + attrName);
                    attrCb.setSelected(!state.context.getDontConsideredAttr().contains(attrName));
                    attrCb.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            if (!attrCb.isSelected()) state.context.dontConsiderAttribute(attrName);
                            else                      state.context.considerAttribute(attrName);
                            state.temporaryContextChanged();
                        }
                    });
                    panel.add(attrCb, gbcSub);
                    attributeCheckBoxes.add(attrCb);
                }
            }
            for (final String attrName : context.getUngroupedAttributes()) {
                gbc.gridy++;
                final WebCheckBox fictCb = new WebCheckBox(attrName.toUpperCase());
                fictCb.setEnabled(false);
                fictCb.setForeground(Color.GRAY);
                fictCb.setSelected(!state.context.getDontConsideredAttr().contains(attrName));
                panel.add(fictCb, gbc);
                gbc.gridy++;
                gbcSub.gridy = gbc.gridy;
                final WebCheckBox orphCb = new WebCheckBox("  \u2514\u2500 " + attrName);
                orphCb.setSelected(!state.context.getDontConsideredAttr().contains(attrName));
                orphCb.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        if (!orphCb.isSelected()) state.context.dontConsiderAttribute(attrName);
                        else                      state.context.considerAttribute(attrName);
                        state.temporaryContextChanged();
                    }
                });
                panel.add(orphCb, gbcSub);
                attributeCheckBoxes.add(orphCb);
            }
        }

        outerPanel.add(panel, BorderLayout.CENTER);
        return new WebScrollPane(outerPanel);
    }

    private WebScrollPane getObjectPanel() {
        WebPanel panel = new WebPanel(new BorderLayout());
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx  = 0;
        gbc.gridy  = 0;
        for (FullObject<String, String> s : context.getObjects()) {
            gbc.gridy++;
            final WebCheckBox box  = new WebCheckBox(s.getIdentifier());
            final FullObject<String, String> temp = s;
            box.setSelected(!state.context.getDontConsideredObj().contains(temp));
            box.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent arg0) {
                    if (!box.isSelected()) state.context.dontConsiderObject(temp);
                    else                   state.context.considerObject(temp);
                    state.temporaryContextChanged();
                }
            });
            panel.add(box, gbc);
            objectCheckBoxes.add(box);
        }
        return new WebScrollPane(panel);
    }

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