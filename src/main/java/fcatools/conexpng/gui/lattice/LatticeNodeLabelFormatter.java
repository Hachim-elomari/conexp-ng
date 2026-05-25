package fcatools.conexpng.gui.lattice;

import java.util.Set;
import fcatools.conexpng.model.FormalContext;
import fcatools.conexpng.model.AttributeGroup;

/**
 * Formats attribute labels in the lattice graph
 * 
 * ✅ FIX BUG 4 : N'affiche QUE les noms d'attributs/objets (sans préfixes de groupe)
 */
public class LatticeNodeLabelFormatter {
    
    /**
     * ✅ FIX : Format attributes WITHOUT group prefixes
     * 
     * Avant : "GENDER:female", "AGE:adult"
     * Après : "female", "adult"
     * 
     * @param attributes Set of attribute names
     * @param context FormalContext for group lookup (unused now)
     * @return Formatted string "attr1, attr2, ..."
     */
    public static String formatAttributesWithGroups(Set<String> attributes, FormalContext context) {
        if (attributes == null || attributes.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        
        // Tri les attributs pour affichage cohérent
        java.util.List<String> sortedAttrs = new java.util.ArrayList<>(attributes);
        java.util.Collections.sort(sortedAttrs);
        
        for (String attr : sortedAttrs) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            
            // ✅ FIX : Afficher JUSTE le nom de l'attribut (sans préfixe)
            sb.append(attr);
        }
        
        return sb.toString();
    }
    
    /**
     * Format attributes without groups (simple list)
     * 
     * @param attributes Set of attribute names
     * @return Formatted string "attr1, attr2, ..."
     */
    public static String formatAttributesSimple(Set<String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        
        java.util.List<String> sortedAttrs = new java.util.ArrayList<>(attributes);
        java.util.Collections.sort(sortedAttrs);
        
        for (String attr : sortedAttrs) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(attr);
        }
        
        return sb.toString();
    }
}