package fcatools.conexpng.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a group of attributes that logically belong together.
 * Example: "Conditions Météo" groups ["Chaleur", "Pluie", "Humidité"]
 * 
 * This class is used for Fonctionnalité 1: Groupes d'Attributs
 */
public class AttributeGroup {

    private String groupId;           // Unique identifier (e.g., "group_1")
    private String groupName;          // Display name (e.g., "Conditions Météo")
    private List<String> attributeNames;  // Names of attributes in this group
    private boolean isExpanded;        // UI state: is the group expanded or collapsed

    /**
     * Constructor with all parameters
     */
    public AttributeGroup(String groupId, String groupName) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.attributeNames = new ArrayList<>();
        this.isExpanded = true;  // Default: expanded
    }

    // ============== GETTERS & SETTERS ==============

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public List<String> getAttributeNames() {
        return attributeNames;
    }

    public void setAttributeNames(List<String> attributeNames) {
        this.attributeNames = attributeNames;
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public void setExpanded(boolean expanded) {
        isExpanded = expanded;
    }

    // ============== ATTRIBUTE MANAGEMENT ==============

    /**
     * Add an attribute to this group
     */
    public boolean addAttribute(String attributeName) {
        if (attributeName == null || attributeName.isEmpty()) {
            return false;
        }
        if (attributeNames.contains(attributeName)) {
            return false;  // Already in group
        }
        attributeNames.add(attributeName);
        return true;
    }

    /**
     * Remove an attribute from this group
     */
    public boolean removeAttribute(String attributeName) {
        return attributeNames.remove(attributeName);
    }

    /**
     * Check if attribute is in this group
     */
    public boolean containsAttribute(String attributeName) {
        return attributeNames.contains(attributeName);
    }

    /**
     * Get number of attributes in this group
     */
    public int getAttributeCount() {
        return attributeNames.size();
    }

    /**
     * Clear all attributes from this group
     */
    public void clearAttributes() {
        attributeNames.clear();
    }

    // ============== UTILITY ==============

    @Override
    public String toString() {
        return groupName + " (" + attributeNames.size() + " attrs)";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AttributeGroup)) {
            return false;
        }
        AttributeGroup other = (AttributeGroup) obj;
        return this.groupId.equals(other.groupId);
    }

    @Override
    public int hashCode() {
        return groupId.hashCode();
    }
    
    
    /**
     * Insérer un attribut à une position spécifique
     * @param index Position où insérer (0 = première position)
     * @param attributeName Nom de l'attribut
     * @return true si succès, false sinon
     */
    public boolean addAttributeAt(int index, String attributeName) {
        if (attributeName == null || attributeName.isEmpty()) {
            return false;
        }
        if (attributeNames.contains(attributeName)) {
            return false;  // Déjà présent
        }
        if (index < 0 || index > attributeNames.size()) {
            return false;  // Index invalide
        }
        attributeNames.add(index, attributeName);
        return true;
    }
     
    /**
     * Obtenir l'index d'un attribut dans ce groupe
     * @param attributeName Nom de l'attribut
     * @return Index (0-based) ou -1 si non trouvé
     */
    public int getAttributeIndex(String attributeName) {
        return attributeNames.indexOf(attributeName);
    }
     
    /**
     * Obtenir l'attribut à une position spécifique
     * @param index Position (0-based)
     * @return Nom de l'attribut ou null si index invalide
     */
    public String getAttributeAt(int index) {
        if (index >= 0 && index < attributeNames.size()) {
            return attributeNames.get(index);
        }
        return null;
    }
     
    /**
     * Ajouter un attribut à la FIN du groupe
     * @param attributeName Nom de l'attribut
     * @return true si succès
     */
    public boolean addAttributeAtEnd(String attributeName) {
        return addAttribute(attributeName);  // addAttribute() ajoute déjà à la fin
    }
     
    /**
     * Ajouter un attribut AU DÉBUT du groupe
     * @param attributeName Nom de l'attribut
     * @return true si succès
     */
    public boolean addAttributeAtBeginning(String attributeName) {
        return addAttributeAt(0, attributeName);
    }
}