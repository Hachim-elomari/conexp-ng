package fcatools.conexpng.gui.contexteditor;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;

import fcatools.conexpng.Conf;
import fcatools.conexpng.gui.MainToolbar;

/**
 * Undo manager refactorisé pour gérer correctement :
 * - les groupes d'attributs (via Conf.copy() qui clone le AttributeGroupManager)
 * - les cas où conf.lastConf == null (pas de NPE)
 * - les undo/redo répétés (snapshot frais à chaque application)
 */
public class ContextEditorUndoManager extends UndoManager {

    private static final long serialVersionUID = 8164716784804291609L;

    private final Conf conf;
    private boolean undoRedoInProgress;

    public ContextEditorUndoManager(Conf conf) {
        this.conf = conf;
        this.undoRedoInProgress = false;
    }

    public boolean isUndoRedoInProgress() {
        return undoRedoInProgress;
    }

    /**
     * À appeler APRÈS une mutation, à condition que state.saveConf() ait été
     * appelé AVANT la mutation pour capturer l'état précédent.
     */
    public void makeRedoable() {
        if (undoRedoInProgress) return;

        // Pas d'état précédent → rien à enregistrer (premier appel ou état non sauvegardé)
        if (conf.lastConf == null || conf.lastConf.context == null) {
            return;
        }

        // Snapshots immuables (deep copy via Conf.copy qui clone aussi les groupes)
        final Conf snapshotAfter  = conf.copy(conf);
        final Conf snapshotBefore = conf.copy(conf.lastConf);

        UndoableEdit edit = new AbstractUndoableEdit() {
            private static final long serialVersionUID = -4461145596327911434L;

            @Override
            public void redo() throws javax.swing.undo.CannotRedoException {
                super.redo();
                applySnapshot(snapshotAfter);
            }

            @Override
            public void undo() throws javax.swing.undo.CannotUndoException {
                super.undo();
                applySnapshot(snapshotBefore);
            }
        };

        addEdit(edit);
        updateButtons();
    }

    /**
     * Restaure une snapshot. Toujours via une nouvelle deep copy pour ne pas
     * "consommer" la snapshot (sinon les undo/redo répétés ne marchent pas).
     */
    private void applySnapshot(Conf snapshot) {
        undoRedoInProgress = true;
        try {
            Conf fresh = conf.copy(snapshot);
            conf.newContext(fresh.context);
        } finally {
            undoRedoInProgress = false;
            updateButtons();
        }
    }

    private void updateButtons() {
        try {
            MainToolbar.getRedoButton().setEnabled(canRedo());
            MainToolbar.getUndoButton().setEnabled(canUndo());
        } catch (Exception ignore) {
            // Boutons pas encore initialisés → safe à ignorer
        }
    }
}