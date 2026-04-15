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
 * 
 * (F1) ÉTAPE 2 : Ligne 0 = noms des groupes, Ligne 1 = noms attributs, Data à partir de ligne 2
 */
public class ContextMatrixModel extends AbstractTableModel implements Reorderable {

    private static final long serialVersionUID = -1509387655329719071L;

    private FormalContext context;
    // Only needed for 'contextChanged' method when renaming s.th.
    private Conf state;

    // (F1) NOUVEAUX CHAMPS pour Fonctionnalité 1 : Groupes d'Attributs
    private boolean displayWithGroups = true;
    private Map<String, Boolean> groupExpansionState = new HashMap<String, Boolean>();

    public ContextMatrixModel(Conf state) {
        loadNewContext(state);
    }

    public void loadNewContext(Conf state) {
        this.state = state;
        this.context = state.context;
    }

    @Override
    public boolean isCellEditable(int i, int j) {
        return (i + j > 1) && (i <= 1 || j == 0);
    }

    // (F1) ÉTAPE 2 : getRowCount() retourne +2 (ligne groupes + ligne attributs)
    @Override
    public int getRowCount() {
        return context.getObjectCount() + 2;  // +1 pour groupes, +1 pour attributs
    }

    @Override
    public int getColumnCount() {
        if (!displayWithGroups) {
            return context.getAttributeCount() + 1;
        }

        int count = 1;

        for (AttributeGroup group : context.getAllAttributeGroups()) {
            if (!group.isExpanded()) {
                count += 1;
            } else {
                count += group.getAttributeCount();
            }
        }

        count += context.getUngroupedAttributes().size();

        return count;
    }

    // (F1) NOUVEAU : getColumnName avec support groupes
    @Override
    public String getColumnName(int columnIndex) {
        System.out.println("[F1-HEADER] Col " + columnIndex + 
                           " displayWithGroups=" + displayWithGroups + 
                           " groupCount=" + context.getAllAttributeGroups().size());
        
        if (columnIndex == 0) {
            return "";
        }

        if (!displayWithGroups) {
            return context.getAttributeAtIndex(columnIndex - 1);
        }

        int colIdx = 0;
        int targetIdx = columnIndex - 1;
        
        System.out.println("[F1-HEADER] targetIdx=" + targetIdx);

        for (AttributeGroup group : context.getAllAttributeGroups()) {
            System.out.println("[F1-HEADER] Group: " + group.getGroupName() + 
                              " expanded=" + group.isExpanded() + 
                              " attrs=" + group.getAttributeNames());
            
            if (!group.isExpanded()) {
                if (colIdx == targetIdx) {
                    String result = "▼ " + group.getGroupName() + " [" + group.getAttributeCount() + "]";
                    System.out.println("[F1-HEADER] RETOUR (collapsed): " + result);
                    return result;
                }
                colIdx++;
            } else {
                for (String attr : group.getAttributeNames()) {
                    System.out.println("[F1-HEADER]   colIdx=" + colIdx + " vs targetIdx=" + targetIdx + " attr=" + attr);
                    if (colIdx == targetIdx) {
                        String result = attr + " (" + group.getGroupName() + ")";
                        System.out.println("[F1-HEADER] RETOUR (expanded): " + result);
                        return result;
                    }
                    colIdx++;
                }
            }
        }

        for (String attr : context.getUngroupedAttributes()) {
            System.out.println("[F1-HEADER]   non-grouped colIdx=" + colIdx + " vs targetIdx=" + targetIdx + " attr=" + attr);
            if (colIdx == targetIdx) {
                System.out.println("[F1-HEADER] RETOUR (ungrouped): " + attr);
                return attr;
            }
            colIdx++;
        }

        System.out.println("[F1-HEADER] RETOUR (default empty string)");
        return "";
    }

    // (F1) ÉTAPE 2 : getValueAt modifié
    // rowIndex == 0 → noms des GROUPES
    // rowIndex == 1 → noms des ATTRIBUTS
    // rowIndex >= 2 → DATA
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        // (F1) ÉTAPE 2 : Ligne des noms de groupes (rowIndex == 0)
        if (rowIndex == 0) {
            if (columnIndex == 0) {
                return ""; // Coin (0,0)
            }
            return getGroupNameAtColumn(columnIndex);
        }

        // (F1) ÉTAPE 2 : Ligne des noms d'attributs (rowIndex == 1)
        if (rowIndex == 1) {
            if (columnIndex == 0) {
                return ""; // Coin (1,0)
            }
            return getAttributeNameAtColumn(columnIndex);
        }

        // DATA : rowIndex >= 2 (décalage de -2 pour accéder aux objets)
        if (columnIndex == 0) {
            return String.format("%s", context.getObjectAtIndex(rowIndex - 2).getIdentifier());
        }

        if (!displayWithGroups) {
            return context.objectHasAttribute(context.getObjectAtIndex(rowIndex - 2),
                    context.getAttributeAtIndex(columnIndex - 1)) ? "X" : "";
        }

        // Mode avec groupes
        String displayValue = getDisplayValueForGroupMode(rowIndex, columnIndex);
        return displayValue;
    }

    /**
     * (F1) ÉTAPE 2 : Récupérer le nom du groupe à une colonne donnée
     */
    private String getGroupNameAtColumn(int columnIndex) {
        int colIdx = 0;
        int targetIdx = columnIndex - 1;

        for (AttributeGroup group : context.getAllAttributeGroups()) {
            if (!group.isExpanded()) {
                if (colIdx == targetIdx) {
                    return group.getGroupName();  // Retourner le nom du groupe en MAJUSCULES
                }
                colIdx++;
            } else {
                for (String attr : group.getAttributeNames()) {
                    if (colIdx == targetIdx) {
                        return group.getGroupName();  // Attributs du groupe → afficher le nom du groupe
                    }
                    colIdx++;
                }
            }
        }

        // Attributs non groupés → retourner vide (pas de groupe)
        for (String attr : context.getUngroupedAttributes()) {
            if (colIdx == targetIdx) {
                return "";
            }
            colIdx++;
        }

        return "";
    }

    /**
     * (F1) ÉTAPE 2 : Récupérer le nom de l'attribut à une colonne donnée
     */
    private String getAttributeNameAtColumn(int columnIndex) {
        int colIdx = 0;
        int targetIdx = columnIndex - 1;

        for (AttributeGroup group : context.getAllAttributeGroups()) {
            if (!group.isExpanded()) {
                if (colIdx == targetIdx) {
                    return "📁 " + group.getGroupName() + " [" + group.getAttributeCount() + "]";
                }
                colIdx++;
            } else {
                for (String attr : group.getAttributeNames()) {
                    if (colIdx == targetIdx) {
                        return attr;
                    }
                    colIdx++;
                }
            }
        }

        for (String attr : context.getUngroupedAttributes()) {
            if (colIdx == targetIdx) {
                return attr;
            }
            colIdx++;
        }

        return "";
    }

    /**
     * (F1) ÉTAPE 2 : Helper pour afficher les noms de colonnes avec groupes (ancien rowIndex == 0)
     */
    private String getColumnNameForDisplay(int columnIndex) {
        int colIdx = 0;
        int targetIdx = columnIndex - 1;

        for (AttributeGroup group : context.getAllAttributeGroups()) {
            if (!group.isExpanded()) {
                if (colIdx == targetIdx) {
                    return "▼ " + group.getGroupName() + " [" + group.getAttributeCount() + "]";
                }
                colIdx++;
            } else {
                for (String attr : group.getAttributeNames()) {
                    if (colIdx == targetIdx) {
                        return attr + " (" + group.getGroupName() + ")";
                    }
                    colIdx++;
                }
            }
        }

        for (String attr : context.getUngroupedAttributes()) {
            if (colIdx == targetIdx) {
                return attr;
            }
            colIdx++;
        }

        return "";
    }

    /**
     * (F1) ÉTAPE 2 : Afficher les valeurs en mode groupe (décalé de 2 pour les données)
     */
    private String getDisplayValueForGroupMode(int rowIndex, int columnIndex) {
        int colIdx = 0;
        int targetIdx = columnIndex - 1;

        for (AttributeGroup group : context.getAllAttributeGroups()) {
            if (!group.isExpanded()) {
                if (colIdx == targetIdx) {
                    boolean allPresent = true;
                    for (String attr : group.getAttributeNames()) {
                        if (!context.objectHasAttribute(
                                context.getObjectAtIndex(rowIndex - 2), attr)) {
                            allPresent = false;
                            break;
                        }
                    }
                    return allPresent ? "X" : "";
                }
                colIdx++;
            } else {
                for (String attr : group.getAttributeNames()) {
                    if (colIdx == targetIdx) {
                        return context.objectHasAttribute(
                                context.getObjectAtIndex(rowIndex - 2), attr) ? "X" : "";
                    }
                    colIdx++;
                }
            }
        }

        for (String attr : context.getUngroupedAttributes()) {
            if (colIdx == targetIdx) {
                return context.objectHasAttribute(
                        context.getObjectAtIndex(rowIndex - 2), attr) ? "X" : "";
            }
            colIdx++;
        }

        return "";
    }

    // (F1) ÉTAPE 2 : setValueAt modifié (rowIndex >= 2 pour les données)
    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        // Pas d'édition sur les lignes 0 et 1 (groupes et attributs)
        if (rowIndex <= 1 || columnIndex == 0) {
            return;
        }

        if (!displayWithGroups) {
            if (columnIndex > context.getAttributeCount()) {
                return;
            }
            
            String attr = context.getAttributeAtIndex(columnIndex - 1);
            de.tudresden.inf.tcs.fcalib.FullObject<String, String> obj = 
                context.getObjectAtIndex(rowIndex - 2);  // Décalage -2
            
            state.saveConf();
            context.toggleAttributeForObject(attr, obj.getIdentifier());
            state.contextChanged();
            state.getContextEditorUndoManager().makeRedoable();
            fireTableCellUpdated(rowIndex, columnIndex);
            return;
        }

        String attr = getAttributeAtVisualColumn(columnIndex);
        if (attr != null) {
            de.tudresden.inf.tcs.fcalib.FullObject<String, String> obj = 
                context.getObjectAtIndex(rowIndex - 2);  // Décalage -2
            
            state.saveConf();
            context.toggleAttributeForObject(attr, obj.getIdentifier());
            state.contextChanged();
            state.getContextEditorUndoManager().makeRedoable();
            fireTableCellUpdated(rowIndex, columnIndex);
        }
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
        if (from < 2 || to < 2)  // Décalage (ligne 0 et 1 réservées)
            return;
        from -= 2;  // Décalage -2
        to -= 2;    // Décalage -2
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

    public void setDisplayWithGroups(boolean value) {
        if (displayWithGroups != value) {
            displayWithGroups = value;
            fireTableStructureChanged();
        }
    }

    public boolean isDisplayWithGroups() {
        return displayWithGroups;
    }

    public void toggleGroupExpansion(String groupId) {
        context.getAttributeGroupManager().toggleGroupExpansion(groupId);
        fireTableStructureChanged();
    }

    public boolean isGroupExpanded(String groupId) {
        AttributeGroup group = context.getAttributeGroupManager().getGroup(groupId);
        return group != null && group.isExpanded();
    }

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

    private List<Integer> getVisibleColumnIndices() {
        List<Integer> visibleIndices = new java.util.ArrayList<Integer>();

        if (!displayWithGroups) {
            for (int i = 0; i < context.getAttributeCount(); i++) {
                visibleIndices.add(i);
            }
            return visibleIndices;
        }

        for (AttributeGroup group : context.getAllAttributeGroups()) {
            if (group.isExpanded()) {
                for (String attr : group.getAttributeNames()) {
                    for (int i = 0; i < context.getAttributeCount(); i++) {
                        if (context.getAttributeAtIndex(i).equals(attr)) {
                            visibleIndices.add(i);
                            break;
                        }
                    }
                }
            }
        }

        for (String attr : context.getUngroupedAttributes()) {
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