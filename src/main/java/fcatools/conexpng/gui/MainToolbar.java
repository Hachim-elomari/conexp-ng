package fcatools.conexpng.gui;

import static fcatools.conexpng.Util.loadIcon;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.undo.UndoManager;

import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.WebButtonGroup;
import com.alee.laf.button.WebButton;
import com.alee.laf.list.WebList;
import com.alee.laf.toolbar.ToolbarStyle;
import com.alee.laf.toolbar.WebToolBar;
import com.alee.managers.popup.PopupWay;
import com.alee.managers.popup.WebButtonPopup;

import de.tudresden.inf.tcs.fcalib.FullObject;
import de.tudresden.inf.tcs.fcalib.action.StartExplorationAction;
import fcatools.conexpng.Conf;
import fcatools.conexpng.Main;
import fcatools.conexpng.Util;
import fcatools.conexpng.gui.MainFrame.StillCalculatingDialog;
import fcatools.conexpng.gui.actions.NewAction;
import fcatools.conexpng.gui.actions.OpenSaveExportAction;
import fcatools.conexpng.io.locale.LocaleHandler;
import fcatools.conexpng.io.locale.SupportedLanguages;

public class MainToolbar extends WebToolBar {

    private Conf state;
    // (F1) : référence à MainFrame pour appeler checkOrphanGroupingStateIfNeeded()
    private final MainFrame mainFrame;

    private static WebButton redoButton;
    private static WebButton undoButton;
    private static WebButton saveButton;

    private static final long serialVersionUID = -3495670613141172867L;

    @SuppressWarnings("serial")
    public MainToolbar(final MainFrame mainFrame, final Conf state) {
        this.state     = state;
        this.mainFrame = mainFrame;
        this.setFloatable(false);

        setToolbarStyle(ToolbarStyle.attached);
        setFloatable(false);

        final NewAction newAction = new NewAction(mainFrame, state);
        WebButton newContextButton = new WebButton(loadIcon("icons/jlfgr/New24.gif")) {
            {
                setDrawFocus(false);
                setToolTipText(LocaleHandler.getString("MainToolbar.MainToolbar.newContextButton.toolTip"));
                addActionListener(newAction);
            }
        };
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), "newContext");
        getActionMap().put("newContext", newAction);
        add(newContextButton);

        final OpenSaveExportAction openAction = new OpenSaveExportAction(mainFrame, state, true, false);
        WebButton openButtonLeft = new WebButton(loadIcon("icons/jlfgr/Open24.gif")) {
            {
                setDrawFocus(false);
                setToolTipText(LocaleHandler.getString("MainToolbar.MainToolbar.openButtonLeft.toolTip"));
                addActionListener(openAction);
            }
        };
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), "openContext");
        getActionMap().put("openContext", openAction);

        final WebButton openButtonRight = new WebButton(loadIcon("icons/arrow_down.png"));
        openButtonRight.setDrawFocus(false);
        openButtonRight.setToolTipText(LocaleHandler.getString("MainToolbar.MainToolbar.openButtonRight.toolTip"));
        final WebButtonPopup popup = new WebButtonPopup(openButtonRight, PopupWay.downRight);
        WebList list = new WebList(state.lastOpened);
        list.setVisibleRowCount(4); list.setEditable(false);
        list.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting() && !((WebList) e.getSource()).isSelectionEmpty()) {
                    popup.hidePopup();
                    if (!state.canBeSaved()) { StillCalculatingDialog scd = mainFrame.new StillCalculatingDialog(); if (scd.isYes()) return; }
                    String path = String.copyValueOf(((String) ((WebList) e.getSource()).getSelectedValue()).toCharArray());
                    OpenSaveExportAction.openContext(mainFrame, state, path);
                    ((WebList) e.getSource()).clearSelection();
                }
            }
        });
        popup.setContent(new GroupPanel(list));
        openButtonRight.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (popup.isShowing()) popup.hidePopup(); else popup.showPopup(openButtonRight);
            }
        });
        add(new WebButtonGroup(true, openButtonLeft, openButtonRight));

        addSeparator();

        // (F1) : Save avec vérification orphelins
        final OpenSaveExportAction saveAction = new OpenSaveExportAction(mainFrame, state, false, false);
        saveButton = new WebButton(loadIcon("icons/jlfgr/Save24.gif")) {
            {
                setDrawFocus(false);
                setEnabled(false);
                setToolTipText(LocaleHandler.getString("MainToolbar.MainToolbar.saveButton.toolTip"));
                addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        if (!mainFrame.checkOrphanGroupingStateIfNeeded()) return;
                        saveAction.actionPerformed(e);
                    }
                });
            }
        };
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "saveContext");
        getActionMap().put("saveContext", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (!mainFrame.checkOrphanGroupingStateIfNeeded()) return;
                saveAction.actionPerformed(e);
            }
        });
        add(saveButton);

        // (F1) : Save As avec vérification orphelins
        final OpenSaveExportAction saveAsAction = new OpenSaveExportAction(mainFrame, state, false, true);
        WebButton saveAsButton = new WebButton(loadIcon("icons/jlfgr/SaveAs24.gif")) {
            {
                setDrawFocus(false);
                setToolTipText(LocaleHandler.getString("MainToolbar.MainToolbar.saveAsButton.toolTip"));
                addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        if (!mainFrame.checkOrphanGroupingStateIfNeeded()) return;
                        saveAsAction.actionPerformed(e);
                    }
                });
            }
        };
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "saveAsContext");
        getActionMap().put("saveAsContext", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (!mainFrame.checkOrphanGroupingStateIfNeeded()) return;
                saveAsAction.actionPerformed(e);
            }
        });
        add(saveAsButton);

        addSeparator();

        undoButton = new WebButton(loadIcon("icons/jlfgr/Undo24.gif")) {
            {
                setEnabled(false); setDrawFocus(false);
                setToolTipText(LocaleHandler.getString("MainToolbar.MainToolbar.undoButton.toolTip"));
                addActionListener(new ActionListener() { public void actionPerformed(ActionEvent arg0) { undo(); } });
            }
        };
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undoContext");
        getActionMap().put("undoContext", new AbstractAction() { public void actionPerformed(ActionEvent arg0) { undo(); } });

        redoButton = new WebButton(loadIcon("icons/jlfgr/Redo24.gif")) {
            {
                setEnabled(false); setDrawFocus(false);
                setToolTipText(LocaleHandler.getString("MainToolbar.MainToolbar.redoButton.toolTip"));
                addActionListener(new ActionListener() { public void actionPerformed(ActionEvent arg0) { redo(); } });
            }
        };
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redoContext");
        getActionMap().put("redoContext", new AbstractAction() { public void actionPerformed(ActionEvent arg0) { redo(); } });
        add(undoButton); add(redoButton);

        addSeparator();

        WebButton showNumberOfConceptsButton = new WebButton(loadIcon("icons/jlfgr/TipOfTheDay24.gif")) {
            {
                setDrawFocus(false); setEnabled(true);
                setToolTipText(LocaleHandler.getString("MainToolbar.MainToolbar.showNumberOfConceptsButton.toolTip"));
                addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        Util.showMessageDialog(mainFrame, LocaleHandler.getString("MainToolbar.MainToolbar.showNumberOfConceptsButton.action") + state.getNumberOfConcepts() + ".", false);
                    }
                });
            }
        };
        add(showNumberOfConceptsButton);
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "countContext");
        getActionMap().put("countContext", new AbstractAction() {
            public void actionPerformed(ActionEvent arg0) {
                Util.showMessageDialog(mainFrame, LocaleHandler.getString("MainToolbar.MainToolbar.showNumberOfConceptsButton.action") + state.getNumberOfConcepts() + ".", false);
            }
        });

        WebButton attrExplButton = new WebButton(loadIcon("icons/jlfgr/Replace24.gif")) {
            {
                setToolTipText(LocaleHandler.getString("MainToolbar.MainToolbar.attrExplButton.toolTip"));
                setDrawFocus(false);
                addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent arg0) {
                        AttributeExplorationExpert expert = new AttributeExplorationExpert(mainFrame, state);
                        state.context.setExpert(expert); expert.addExpertActionListener(state.context);
                        StartExplorationAction<String, String, FullObject<String, String>> action = new StartExplorationAction<String, String, FullObject<String, String>>();
                        action.setContext(state.context); expert.fireExpertAction(action);
                    }
                });
            }
        };
        add(attrExplButton);
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "aexplorationContext");
        getActionMap().put("aexplorationContext", new AbstractAction() {
            public void actionPerformed(ActionEvent arg0) {
                AttributeExplorationExpert expert = new AttributeExplorationExpert(mainFrame, state);
                state.context.setExpert(expert); expert.addExpertActionListener(state.context);
                StartExplorationAction<String, String, FullObject<String, String>> action = new StartExplorationAction<String, String, FullObject<String, String>>();
                action.setContext(state.context); expert.fireExpertAction(action);
            }
        });

        addSeparator();

        final WebButton languageButton = new WebButton(loadIcon("icons/arrow_down.png"));
        languageButton.setDrawFocus(false);
        languageButton.setToolTipText(LocaleHandler.getString("MainToolbar.MainToolbar.languageButton.toolTip"));
        final WebButtonPopup languageButtonPopup = new WebButtonPopup(languageButton, PopupWay.downCenter);
        WebList languageButtonPopupList = new WebList(SupportedLanguages.values());
        languageButtonPopupList.setEditable(false);
        languageButtonPopupList.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                WebList list = (WebList) e.getSource();
                if (!e.getValueIsAdjusting() && !list.isSelectionEmpty()) {
                    languageButtonPopup.hidePopup();
                    LocaleHandler.setSelectedLanguage(list.getSelectedValue().toString());
                    Main.storeOptions(mainFrame, state); list.clearSelection();
                }
            }
        });
        languageButtonPopup.setContent(new GroupPanel(languageButtonPopupList));
        languageButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (languageButtonPopup.isShowing()) languageButtonPopup.hidePopup(); else languageButtonPopup.showPopup(languageButton);
            }
        });
        add(languageButton);

        WebButton aboutButton = new WebButton(loadIcon("icons/jlfgr/About24.gif")) {
            {
                setDrawFocus(false); setEnabled(true);
                setToolTipText(LocaleHandler.getString("MainToolbar.MainToolbar.aboutButton.toolTip"));
                addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent arg0) {
                        Util.showMessageDialog(mainFrame, LocaleHandler.getString("MainToolbar.MainToolbar.aboutButton.aboutText"), false);
                    }
                });
            }
        };
        add(aboutButton);

        WebButton helpButton = new WebButton(loadIcon("icons/jlfgr/Help24.gif")) {
            { setDrawFocus(false); setEnabled(true); setToolTipText(LocaleHandler.getString("MainToolbar.MainToolbar.helpButton.toolTip")); }
        };
        add(helpButton);
    }

    private void undo() {
        UndoManager um = null;
        if (state.guiConf.lastTab == 0) um = state.getContextEditorUndoManager();
        else if (state.guiConf.lastTab == 1) um = state.getLatticeViewUndoManager();
        if (um != null && um.canUndo()) {
            um.undo();
            getRedoButton().setEnabled(um.canRedo());
            getUndoButton().setEnabled(um.canUndo());
        }
    }

    private void redo() {
        UndoManager um = null;
        if (state.guiConf.lastTab == 0) um = state.getContextEditorUndoManager();
        else if (state.guiConf.lastTab == 1) um = state.getLatticeViewUndoManager();
        if (um != null && um.canRedo()) {
            um.redo();
            getRedoButton().setEnabled(um.canRedo());
            getUndoButton().setEnabled(um.canUndo());
        }
    }

    public static WebButton getSaveButton()  { return saveButton; }
    public static WebButton getUndoButton()  { return undoButton; }
    public static WebButton getRedoButton()  { return redoButton; }
}