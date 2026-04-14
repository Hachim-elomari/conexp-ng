package fcatools.conexpng.gui.contexteditor;

import javax.swing.table.AbstractTableModel;

import fcatools.conexpng.Conf;
import fcatools.conexpng.model.FormalContext;

// (F1) Imports pour Fonctionnalité 1 : Groupes d'Attributs
import fcatools.conexpng.model.AttributeGroup;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static fcatools.conexpng.Util.clamp;

/**
 * ContextMatrixModel allows the separation between the data and its
 * presentation in the JTable. Whenever the context is changed the changes are
 * reflected (automatically) in the corresponding JTable. In particular, if the
 * user changes the context through the context editor what really happens is
 * that the context is changed (not the JTable per se) and the JTable is redrawn
 * based on the updated context.
 */
public class ContextMatrixModel extends AbstractTableModel implements Reorderable {

    private static final long serialVersionUID = -1509387655329719071L;

    private FormalContext context;
    // Only needed for 'contextChanged' method when renaming s.th.
    private Conf state;

    // (F1) NOUVEAUX CHAMPS pour Fonctionnalité 1 : Groupes d'Attributs
    private boolean displayWithGroups = true;  // Afficher les groupes (replié/déplié)
    private Map<String, Boolean> groupExpansionState = new HashMap<>();  // État expansion des groupes

    public ContextMatrixModel(Conf state) {
        loadNewContext(state);
    }

    public void loadNewContext(Conf state) {
        this.state = state;
        this.context = state.context;
    }

    @Override
    public boolean isCellEditable(int i, int j) {
        return (i + j > 0) && (i == 0 || j == 0);
    }

    @Override
    public int getRowCount() {
        return context.getObjectCount() + 1;
    }

    // (F1) REDÉFINI pour Fonctionnalité 1 : Afficher colonnes groupées
    @Override
    public int getColumnCount() {
        if (!displayWithGroups) {
            return context.getAttributeCount() + 1;
        }

        // Mode avec groupes: 
        // colonnes = (groupes repliés) + (attributs dépliés) + (attributs non groupés)
        int count = 1;  // +1 pour colonne objets

        // Compter les groupes repliés comme 1 colonne chacun
        for (AttributeGroup group : context.getAllAttributeGroups()) {
            if (!group.isExpanded()) {
                count += 1;  // Super-colonne groupe
            } else {
                count += group.getAttributeCount();  // Attributs déployés
            }
        }

        // Ajouter les attributs non groupés
        count += context.getUngroupedAttributes().size();

        return count;
    }

    // (F1) REDÉFINI pour Fonctionnalité 1 : Afficher données avec groupes
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        // Gestion du coin (0,0)
        if (columnIndex == 0 && rowIndex == 0) {
            return "";
        }

        // Colonne objets (index 0)
        if (columnIndex == 0) {
            return String.format("%s", context.getObjectAtIndex(rowIndex - 1).getIdentifier());
        }

        if (!displayWithGroups) {
            // Mode normal (sans groupes)
            if (rowIndex == 0) {
                return String.format("%s", context.getAttributeAtIndex(columnIndex - 1));
            }
            return context.objectHasAttribute(context.getObjectAtIndex(rowIndex - 1),
                    context.getAttributeAtIndex(columnIndex - 1)) ? "X" : "";
        }

        // (F1) Mode avec groupes
        String displayValue = getDisplayValueForGroupMode(rowIndex, columnIndex);
        return displayValue;
    }

    // (F1) NOUVELLE MÉTHODE HELPER pour Fonctionnalité 1
    /**
     * Helper pour afficher les valeurs en mode groupe
     */
    private String getDisplayValueForGroupMode(int rowIndex, int columnIndex) {
        int colIdx = 0;
        int targetIdx = columnIndex - 1;

        // Parcourir les groupes
        for (AttributeGroup group : context.getAllAttributeGroups()) {
            if (!group.isExpanded()) {
                // Groupe replié = super-colonne
                if (colIdx == targetIdx) {
                    // C'est l'en-tête: afficher "📁 GroupeName"
                    if (rowIndex == 0) {
                        return "📁 " + group.getGroupName();
                    }
                    // Pour les données: afficher "X" si ALL attributs du groupe sont présents
                    boolean allPresent = true;
                    for (String attr : group.getAttributeNames()) {
                        if (!context.objectHasAttribute(
                                context.getObjectAtIndex(rowIndex - 1), attr)) {
                            allPresent = false;
                            break;
                        }
                    }
                    return allPresent ? "X" : "";
                }
                colIdx++;
            } else {
                // Groupe déplié = afficher attributs individuels
                for (String attr : group.getAttributeNames()) {
                    if (colIdx == targetIdx) {
                        if (rowIndex == 0) {
                            return attr;
                        }
                        return context.objectHasAttribute(
                                context.getObjectAtIndex(rowIndex - 1), attr) ? "X" : "";
                    }
                    colIdx++;
                }
            }
        }

        // Attributs non groupés
        for (String attr : context.getUngroupedAttributes()) {
            if (colIdx == targetIdx) {
                if (rowIndex == 0) {
                    return attr;
                }
                return context.objectHasAttribute(
                        context.getObjectAtIndex(rowIndex - 1), attr) ? "X" : "";
            }
            colIdx++;
        }

        return "";
    }

    @Override
    public void setValueAt(Object value, int i, int j) {

    }

    public String getAttributeNameAt(int i) {
        return context.getAttributeAtIndex(i);
    }

    public String getObjectNameAt(int i) {
        return context.getObjectAtIndex(i).getIdentifier();
    }

    public boolean renameAttribute(String oldName, String newName) {
        if (context.existsAttributeAlready(newName)) {
            return false;
        } else {
            state.saveConf();
            context.renameAttribute(oldName, newName);
            state.contextChanged();
            state.getContextEditorUndoManager().makeRedoable();
            return true;
        }
    }

    public boolean renameObject(String oldName, String newName) {
        if (context.existsObjectAlready(newName)) {
            return false;
        } else {
            state.saveConf();
            context.renameObject(oldName, newName);
            state.getContextEditorUndoManager().makeRedoable();
            state.contextChanged();
            return true;
        }
    }

    public void reorderRows(int from, int to) {
        if (context.getObjectCount() < 2)
            return;
        if (from < 1 || to < 1)
            return;
        from -= 1;
        to -= 1;
        from = clamp(from, 0, context.getObjectCount() - 1);
        to = clamp(to, 0, context.getObjectCount() - 1);
        de.tudresden.inf.tcs.fcalib.FullObject<String, String> o = context.getObjectAtIndex(from);
        state.saveConf();
        try {
            context.removeObject(o.getIdentifier());
        } catch (de.tudresden.inf.tcs.fcaapi.exception.IllegalObjectException e) {
            e.printStackTrace();
        }
        context.addObjectAt(o, to);
        state.getContextEditorUndoManager().makeRedoable();
    }

    public void reorderColumns(int from, int to) {
        if (context.getAttributeCount() < 2)
            return;
        if (from < 1 || to < 1)
            return;
        from -= 1;
        to -= 1;
        from = clamp(from, 0, context.getAttributeCount() - 1);
        to = clamp(to, 0, context.getAttributeCount() - 1);
        String a = context.getAttributeAtIndex(from);
        state.saveConf();
        context.removeAttributeInternal(a);
        context.addAttributeAt(a, to);
        state.getContextEditorUndoManager().makeRedoable();
    }

    // ═════════════════════════════════════════════════════════════
    // (F1) NOUVELLES MÉTHODES pour Fonctionnalité 1 : Groupes d'Attributs
    // ═════════════════════════════════════════════════════════════

    /**
     * (F1) Toggle display mode: with/without groups
     */
    public void setDisplayWithGroups(boolean value) {
        if (displayWithGroups != value) {
            displayWithGroups = value;
            fireTableStructureChanged();
        }
    }

    /**
     * (F1) Check if currently displaying with groups
     */
    public boolean isDisplayWithGroups() {
        return displayWithGroups;
    }

    /**
     * (F1) Toggle expansion state of a group
     */
    public void toggleGroupExpansion(String groupId) {
        context.getAttributeGroupManager().toggleGroupExpansion(groupId);
        fireTableStructureChanged();
    }

    /**
     * (F1) Check if a group is expanded
     */
    public boolean isGroupExpanded(String groupId) {
        AttributeGroup group = context.getAttributeGroupManager().getGroup(groupId);
        return group != null && group.isExpanded();
    }

    /**
     * (F1) Get the group at a specific visual column (null if not a group)
     */
    public AttributeGroup getGroupAtColumn(int columnIndex) {
        int colIdx = 0;
        for (AttributeGroup group : context.getAllAttributeGroups()) {
            if (!group.isExpanded()) {
                if (colIdx == columnIndex - 1) {
                    return group;
                }
                colIdx++;
            } else {
                colIdx += group.getAttributeCount();
            }
        }
        return null;
    }

    /**
     * (F1) Get the attribute at a specific visual column (null if not an attribute)
     */
    public String getAttributeAtVisualColumn(int columnIndex) {
        if (columnIndex <= 0) {
            return null;
        }

        int colIdx = 0;
        for (AttributeGroup group : context.getAllAttributeGroups()) {
            if (!group.isExpanded()) {
                colIdx++;
            } else {
                for (String attr : group.getAttributeNames()) {
                    if (colIdx == columnIndex - 1) {
                        return attr;
                    }
                    colIdx++;
                }
            }
        }

        for (String attr : context.getUngroupedAttributes()) {
            if (colIdx == columnIndex - 1) {
                return attr;
            }
            colIdx++;
        }

        return null;
    }

    /**
     * (F1) Get visible column indices (handles grouped attributes)
     * Returns: list of attribute indices that should be displayed
     * 
     * ⚠️ CORRIGÉ: Utilise getAttributeAtIndex() au lieu de indexOf() 
     * car getAttributes() retourne un IndexedSet, pas une List
     */
    private List<Integer> getVisibleColumnIndices() {
        List<Integer> visibleIndices = new java.util.ArrayList<>();

        if (!displayWithGroups) {
            // Mode normal: afficher toutes les colonnes
            for (int i = 0; i < context.getAttributeCount(); i++) {
                visibleIndices.add(i);
            }
            return visibleIndices;
        }

        // Mode avec groupes
        for (AttributeGroup group : context.getAllAttributeGroups()) {
            if (group.isExpanded()) {
                // Si groupe déplié: afficher tous les attributs du groupe
                for (String attr : group.getAttributeNames()) {
                    // (F1) Trouver l'index de cet attribut en boucle
                    for (int i = 0; i < context.getAttributeCount(); i++) {
                        if (context.getAttributeAtIndex(i).equals(attr)) {
                            visibleIndices.add(i);
                            break;
                        }
                    }
                }
            }
            // Si groupe replié: ne rien afficher (il y a une super-colonne)
        }

        // Ajouter les attributs non groupés
        for (String attr : context.getUngroupedAttributes()) {
            // (F1) Trouver l'index de cet attribut en boucle
            for (int i = 0; i < context.getAttributeCount(); i++) {
                if (context.getAttributeAtIndex(i).equals(attr)) {
                    visibleIndices.add(i);
                    break;
                }
            }
        }

        return visibleIndices;
    }
}