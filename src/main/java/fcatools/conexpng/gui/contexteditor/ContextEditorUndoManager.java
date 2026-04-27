package fcatools.conexpng.gui.contexteditor;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;

import fcatools.conexpng.Conf;
import fcatools.conexpng.gui.MainToolbar;
import fcatools.conexpng.model.AttributeGroupManager;

/**
 * Undo manager for context editor with proper attribute group support.
 * 
 * FIX UNDO/REDO : Cette classe capture maintenant l'état complet des groupes
 * d'attributs lors des opérations undo/redo, pas uniquement le contexte.
 * 
 * @author Torsten Casselt
 * @modified for attribute groups - April 2026
 */
public class ContextEditorUndoManager extends UndoManager {
    private static final long serialVersionUID = 8164716784804291609L;
    private Conf conf;
    private boolean undoRedoInProgress;

    /**
     * Creates the undo manager with given configuration.
     * 
     * @param conf the global configuration object
     */
    public ContextEditorUndoManager(Conf conf) {
        this.conf = conf;
        this.undoRedoInProgress = false;
    }

    /**
     * Method to define the steps necessary for undo and redo and register the UndoableEdit.
     * 
     * FIX UNDO/REDO : Maintenant capture aussi l'état des groupes d'attributs.
     * 
     * Le problème original était que les groupes n'étaient pas sauvegardés,
     * donc lors d'un undo/redo, les groupes disparaissaient.
     * 
     * Solution : Capturer explicitement l'AttributeGroupManager AVANT et APRÈS l'action.
     */
    public void makeRedoable() {
        final Conf curConf = conf.copy(conf);
        final Conf lastConf = conf.copy(conf.lastConf);
        
        // 🆕 NOUVEAU : Capturer aussi conf.lastConf
        final Conf savedLastConf = conf.copy(conf.lastConf);
        
        // FIX UNDO/REDO : Capturer les groupes
        final AttributeGroupManager curGroupManager = 
            conf.context.getAttributeGroupManager().clone();
        final AttributeGroupManager lastGroupManager = 
            conf.lastConf.context.getAttributeGroupManager().clone();

        if (!undoRedoInProgress) {
            UndoableEdit undoableEdit = new AbstractUndoableEdit() {
                private static final long serialVersionUID = -4461145596327911434L;

                public void redo() throws javax.swing.undo.CannotRedoException {
                    super.redo();
                    undoRedoInProgress = true;
                    
                    // Restaurer le contexte
                    conf.newContext(curConf.context);
                    conf.context.setAttributeGroupManager(curGroupManager.clone());
                    
                    // 🆕 Restaurer aussi conf.lastConf
                    conf.lastConf = savedLastConf.copy(savedLastConf);
                    
                    undoRedoInProgress = false;
                    MainToolbar.getRedoButton().setEnabled(canRedo());
                    MainToolbar.getUndoButton().setEnabled(canUndo());
                }

                public void undo() throws javax.swing.undo.CannotUndoException {
                    super.undo();
                    undoRedoInProgress = true;
                    
                    // Restaurer le contexte
                    conf.newContext(lastConf.context);
                    conf.context.setAttributeGroupManager(lastGroupManager.clone());
                    
                    // 🆕 Restaurer aussi conf.lastConf 
                    // (garder l'ancienne valeur de lastConf)
                    // Le lastConf d'AVANT cette action reste lastConf
                    
                    undoRedoInProgress = false;
                    MainToolbar.getRedoButton().setEnabled(canRedo());
                    MainToolbar.getUndoButton().setEnabled(canUndo());
                }
            };

            addEdit(undoableEdit);
            MainToolbar.getRedoButton().setEnabled(canRedo());
            MainToolbar.getUndoButton().setEnabled(canUndo());
        }
    }

    /**
     * Helper : Vérifier si on est actuellement en train de faire undo/redo
     * (évite les boucles infinies d'édits)
     */
    public boolean isUndoRedoInProgress() {
        return undoRedoInProgress;
    }
}