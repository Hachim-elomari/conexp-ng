package fcatools.conexpng;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import de.tudresden.inf.tcs.fcaapi.Concept;
import de.tudresden.inf.tcs.fcaapi.FCAImplication;
import de.tudresden.inf.tcs.fcaapi.exception.IllegalObjectException;
import de.tudresden.inf.tcs.fcalib.FullObject;
import fcatools.conexpng.gui.MainToolbar;
import fcatools.conexpng.gui.StatusBar;
import fcatools.conexpng.gui.contexteditor.ContextEditorUndoManager;
import fcatools.conexpng.gui.lattice.LatticeGraph;
import fcatools.conexpng.gui.lattice.LatticeViewUndoManager;
import fcatools.conexpng.io.locale.LocaleHandler;
import fcatools.conexpng.model.AssociationRule;
import fcatools.conexpng.model.FormalContext;

/**
 * Contains context, lattice, implications, filePath, snapshots etc.
 * <p>
 * Why 'Conf'? "Dependency Injection", e.g. for testing purposes a component can
 * be passed a "MockConfiguration" very easily and it is better to have a
 * central place for the program's state as opposed to have it scattered
 * throughout different classes. If you want you can see this class as the
 * "Model" in an MVC context.
 * 
 */
public class Conf {
    public String filePath;
    public Vector<String> lastOpened = new Vector<>(5);
    public FormalContext context;
    public Set<AssociationRule> associations;
    public Set<FCAImplication<String>> implications;
    public boolean unsavedChanges = false;
    public LatticeGraph lattice;
    public Set<Concept<String, FullObject<String, String>>> concepts;
    public GUIConf guiConf;
    private StatusBar statusBar;
    public Conf lastConf;
    // undo manager
    private ContextEditorUndoManager contextEditorUndoManager = new ContextEditorUndoManager(this);
    private LatticeViewUndoManager latticeViewUndoManager = new LatticeViewUndoManager();

    private PropertyChangeSupport propertyChangeSupport;

    // ═════════════════════════════════════════════════════════════════════════
    // (F1) NOUVEAUX CHAMPS pour Fonctionnalité 1 : Groupes d'Attributs
    // ═════════════════════════════════════════════════════════════════════════
    
    // Stocke l'ID du groupe actuellement sélectionné pour la génération du treillis
    // Si null → générer le treillis pour TOUS les attributs (comportement normal)
    // Si non-null (ex: "group_1") → générer le treillis SEULEMENT pour ce groupe
    private String currentGroupIdForLattice = null;
    
    // Stocke l'ensemble des attributs sélectionnés pour l'affichage du treillis
    // Utilisé par AttributeSelectionPanel pour filtrer les attributs affichés
    private Set<String> selectedAttributesForLattice = new HashSet<String>();

    public Conf() {
        propertyChangeSupport = new PropertyChangeSupport(this);
        guiConf = new GUIConf();
    }

    public int getNumberOfConcepts() {
        if (concepts.isEmpty()) {
            startCalculation(StatusMessage.CALCULATINGCONCEPTS);
            concepts = context.getConceptsWithoutConsideredElements();
            endCalculation(StatusMessage.CALCULATINGCONCEPTS);
        }
        return concepts.size();
    }

    public void init(int rows, int columns) {
        context = new FormalContext(rows, columns);
        associations = new TreeSet<AssociationRule>();
        implications = new TreeSet<FCAImplication<String>>();
        concepts = new HashSet<Concept<String, FullObject<String, String>>>();
        guiConf = new GUIConf();
        lattice = new LatticeGraph();
        filePath = filePath.substring(0, filePath.lastIndexOf(System.getProperty("file.separator")) + 1)
                + "untitled.cex";
        newContext(context);
    }

    public void setNewFile(String filepath) {
        if (filePath.equals(filepath)) {
            return;
        }
        lastOpened.remove(filepath);
        lastOpened.remove(filePath);
        if (new File(filePath).exists()) {
            lastOpened.add(0, this.filePath);
            if (lastOpened.size() > 5) {
                lastOpened.remove(5);
            }
        }
        filePath = filepath;
    }

    /**
     * Returns the status bar of this program.
     * 
     * @return status bar of this program
     */
    public StatusBar getStatusBar() {
        return statusBar;
    }

    /**
     * Sets the status bar of this program.
     * 
     * @param statusBar
     */
    public void setStatusBar(StatusBar statusBar) {
        this.statusBar = statusBar;
    }

    /**
     * Returns the context editor undo manager.
     * 
     * @return
     */
    public ContextEditorUndoManager getContextEditorUndoManager() {
        return contextEditorUndoManager;
    }

    /**
     * Returns the lattice view undo manager.
     * 
     * @return
     */
    public LatticeViewUndoManager getLatticeViewUndoManager() {
        return latticeViewUndoManager;
    }

    public void saveConf() {
        lastConf = copy(this);
    }

    public Conf copy(Conf conf) {
        Conf copy = new Conf();
        copy.context = new FormalContext();
        copy.context.addAttributes(conf.context.getAttributes());
        try {
            copy.context.addObjects(conf.context.getObjects());
        } catch (IllegalObjectException e) {
            // should never happens
        }
        
        // ✅ FIX UNDO/REDO : Copier aussi les groupes d'attributs !
        if (conf.context.hasAttributeGroupManager()) {
            copy.context.setAttributeGroupManager(
                conf.context.getAttributeGroupManager().clone()
            );
        }
        
        return copy;
    }

    // Communication
    // /////////////////////////////////////////////////////////////77

    public void contextChanged() {
        this.context.clearConsidered();
        firePropertyChange(ContextChangeEvents.CONTEXTCHANGED, null, context);
    }

    public void newContext(FormalContext context) {
        cancelCalculations();
        this.context = context;
        this.context.clearConsidered();
        firePropertyChange(ContextChangeEvents.NEWCONTEXT, null, context);
    }

    public void attributeNameChanged(String oldName, String newName) {
        firePropertyChange(ContextChangeEvents.ATTRIBUTENAMECHANGED, oldName, newName);
    }

    public void showLabelsChanged() {
        firePropertyChange(ContextChangeEvents.LABELSCHANGED, null, null);
    }

    public void temporaryContextChanged() {
        firePropertyChange(ContextChangeEvents.TEMPORARYCONTEXTCHANGED, null, null);
    }

    private int starts = 0;
    private int stops = 0;

    public void startCalculation(StatusMessage status) {
        starts++;
        fireStatusBarPropertyChange(status, START);
    }

    public void endCalculation(StatusMessage status) {
        stops++;
        fireStatusBarPropertyChange(status, STOP);
    }

    public void cancelCalculations() {
        firePropertyChange(ContextChangeEvents.CANCELCALCULATIONS, null, null);
    }

    public boolean canBeSaved() {
        return starts <= stops;
    }

    public void loadedFile() {
        cancelCalculations();
        propertyChangeSupport.firePropertyChange(new PropertyChangeEvent(this, "filepath", filePath, filePath));
        firePropertyChange(ContextChangeEvents.LOADEDFILE, null, lattice);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // (F1) NOUVELLES MÉTHODES pour Fonctionnalité 1 : Groupes d'Attributs
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * (F1) Définir le groupe pour lequel générer le treillis
     * 
     * Utilisé par GenerateLatticeForGroupAction pour dire au système :
     * "Génère le treillis SEULEMENT pour les attributs du groupe 'group_1'"
     * 
     * Exemple :
     * - Appel : setCurrentGroupIdForLattice("group_1")
     * - Effet : Le prochain appel à computeLatticeGraph() filtrera les concepts
     *           pour ce groupe uniquement (au lieu de tous les attributs)
     * - Résultat : Treillis réduit et plus lisible, focalisé sur une famille d'attributs
     * 
     * @param groupId ID du groupe (ex: "group_1") ou null pour revenir au mode normal
     */
    public void setCurrentGroupIdForLattice(String groupId) {
        this.currentGroupIdForLattice = groupId;
    }

    /**
     * (F1) Récupérer le groupe actuellement défini pour la génération du treillis
     * 
     * Utilisé par LatticeGraphComputer pour savoir s'il faut filtrer les concepts.
     * 
     * @return ID du groupe (ex: "group_1") ou null si génération pour tous les attributs
     */
    public String getCurrentGroupIdForLattice() {
        return currentGroupIdForLattice;
    }

    /**
     * (F1) Notifier que le treillis doit être recalculé
     * 
     * Cette méthode est appelée après un changement de groupe pour forcer la mise à jour
     * du treillis affiché à l'écran.
     * 
     * Flux d'utilisation :
     * 1. Utilisateur clique "Generate Lattice for Group"
     * 2. ContextEditor appelle setCurrentGroupIdForLattice("group_1")
     * 3. ContextEditor appelle latticeChanged()
     * 4. Les listeners (LatticeView, etc.) reçoivent la notification
     * 5. LatticeGraphComputer.computeLatticeGraph() est appelé
     * 6. Le treillis filtré s'affiche
     * 
     * Similar à contextChanged() mais spécifiquement pour les changements de groupe
     */
    public void latticeChanged() {
        // Déclencher le recalcul du treillis avec le groupe spécifié
        firePropertyChange(ContextChangeEvents.LATTICECHANGED, null, lattice);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // (F1) NOUVELLES MÉTHODES pour la sélection des attributs dans le lattice
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * (F1) Définir l'ensemble des attributs sélectionnés pour l'affichage du treillis
     * 
     * Utilisé par AttributeSelectionPanel quand l'utilisateur coche/décoche des attributs.
     * 
     * Exemple :
     * - Utilisateur décoche l'attribut "juvenile" dans le panel
     * - AttributeSelectionPanel appelle setSelectedAttributesForLattice({adult, female, male})
     * - LatticeGraphComputer recalcule le treillis avec SEULEMENT ces 3 attributs
     * 
     * @param attributes Set d'attributs à sélectionner (ex: {adult, female, male})
     */
    public void setSelectedAttributesForLattice(Set<String> attributes) {
        if (attributes == null) {
            this.selectedAttributesForLattice = new HashSet<String>();
        } else {
            this.selectedAttributesForLattice = new HashSet<String>(attributes);
        }
    }

    /**
     * (F1) Récupérer l'ensemble des attributs sélectionnés pour l'affichage du treillis
     * 
     * Utilisé par LatticeGraphComputer pour savoir quels attributs utiliser
     * dans la génération du treillis filtré.
     * 
     * @return Set d'attributs sélectionnés (copie pour éviter les modifications externes)
     */
    public Set<String> getSelectedAttributesForLattice() {
        if (selectedAttributesForLattice == null || selectedAttributesForLattice.isEmpty()) {
            // Si aucun attribut n'est défini, initialiser avec TOUS les attributs
            resetSelectedAttributesForLattice();
        }
        return new HashSet<String>(selectedAttributesForLattice);
    }

    /**
     * (F1) Réinitialiser la sélection des attributs à TOUS les attributs du contexte
     * 
     * Utilisé quand :
     * - L'utilisateur clique sur "Show All" dans le panel des attributs
     * - Un nouveau contexte est chargé
     * - L'utilisateur veut revenir au comportement normal (tous les attributs visibles)
     */
    public void resetSelectedAttributesForLattice() {
        selectedAttributesForLattice.clear();
        if (context != null) {
            for (int i = 0; i < context.getAttributeCount(); i++) {
                selectedAttributesForLattice.add(context.getAttributeAtIndex(i));
            }
        }
    }

    @SuppressWarnings("serial")
    public class ContextChangeEvent extends PropertyChangeEvent {

        private ContextChangeEvents cce;

        public ContextChangeEvent(Object source, String propertyName, Object oldValue, Object newValue) {
            super(source, propertyName, oldValue, newValue);
        }

        public ContextChangeEvent(Object source, ContextChangeEvents cce, Object oldValue, Object newValue) {
            this(source, cce.toString(), oldValue, newValue);
            this.cce = cce;
        }

        public ContextChangeEvents getName() {
            return cce;
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    private void firePropertyChange(ContextChangeEvents cce, Object oldValue, Object newValue) {
        if (propertyChangeSupport.getPropertyChangeListeners().length != 0) {
            if (cce != ContextChangeEvents.LOADEDFILE && cce != ContextChangeEvents.CANCELCALCULATIONS) {
                unsavedChanges = true;
                MainToolbar.getSaveButton().setEnabled(true);
            }
            propertyChangeSupport.firePropertyChange(new ContextChangeEvent(this, cce, oldValue, newValue));
        }
    }

    public static final int START = 1;

    public static final int STOP = 2;

    public enum StatusMessage {

        LOADINGFILE(LocaleHandler.getString("Conf.StatusMessage.loadingFile")), SAVINGFILE(LocaleHandler
                .getString("Conf.StatusMessage.savingFile")), CALCULATINGASSOCIATIONS(LocaleHandler
                .getString("Conf.StatusMessage.calcAsso")), CALCULATINGIMPLICATIONS(LocaleHandler
                .getString("Conf.StatusMessage.calcImpl")), CALCULATINGCONCEPTS(LocaleHandler
                .getString("Conf.StatusMessage.calcConcepts")), CALCULATINGLATTICE(LocaleHandler
                .getString("Conf.StatusMessage.calcLattice")), CLARIFYINGOBJECTS(LocaleHandler
                .getString("Conf.StatusMessage.clarifyingObj")), CLARIFYINGATTRIBUTES(LocaleHandler
                .getString("Conf.StatusMessage.clarifyingAttr")), CLARIFYING(LocaleHandler
                .getString("Conf.StatusMessage.clarifying")), REDUCINGOBJECTS(LocaleHandler
                .getString("Conf.StatusMessage.reducingObj")), REDUCINGATTRIBUTES(LocaleHandler
                .getString("Conf.StatusMessage.reducingAttr")), REDUCING(LocaleHandler
                .getString("Conf.StatusMessage.reducing"));

        private StatusMessage(String name) {
            this.name = name;
        }

        private final String name;

        public String toString() {
            return name;
        }
    }

    public class StatusBarMessage extends PropertyChangeEvent {

        private static final long serialVersionUID = 1L;

        public StatusBarMessage(Object source, String propertyName, Object oldValue, Object newValue) {
            super(source, propertyName, oldValue, newValue);
        }

        public StatusBarMessage(Object source, StatusMessage status, Object oldValue, Object newValue) {
            this(source, status.toString(), oldValue, newValue);
        }
    }

    private void fireStatusBarPropertyChange(StatusMessage status, int newValue) {
        if (status != StatusMessage.LOADINGFILE && newValue != START) {
            unsavedChanges = true;
            MainToolbar.getSaveButton().setEnabled(true);
        }
        propertyChangeSupport.firePropertyChange(new StatusBarMessage(this, status, 0, newValue));
    }
}