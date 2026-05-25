package fcatools.conexpng.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages attribute groups for FormalContext.
 * Provides CRUD operations for attribute groups.
 * 
 * Fonctionnalité 1: Groupes d'Attributs
 * ✅ FIX CASSE : Force MAJUSCULE pour les noms de groupes
 * 🆕 NOUVEAU : Support réorganisation des groupes (LinkedHashMap)
 */
public class AttributeGroupManager {

    private final FormalContext context;
    private final Map<String, AttributeGroup> groups;  // LinkedHashMap maintains insertion order
    private final AtomicInteger groupIdCounter;

    /**
     * Initialize manager for a formal context
     */
    public AttributeGroupManager(FormalContext context) {
        this.context = context;
        this.groups = new LinkedHashMap<>();  // 🆕 LinkedHashMap pour maintenir l'ordre
        this.groupIdCounter = new AtomicInteger(0);
    }

    // ============== CREATE ==============

    /**
     * Create a new group and add it to the manager
     * ✅ FIX CASSE : Force MAJUSCULE automatiquement
     * @return the generated group ID (e.g., "group_0", "group_1", etc.)
     */
    public String createGroup(String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            return null;
        }

        String groupNameUpper = groupName.trim().toUpperCase();
        
        if (groupNameExists(groupNameUpper)) {
            System.out.println("[F1-CASSE] Groupe '" + groupNameUpper + "' existe déjà, création échouée");
            return null;
        }

        String groupId = "group_" + groupIdCounter.getAndIncrement();
        AttributeGroup group = new AttributeGroup(groupId, groupNameUpper);
        groups.put(groupId, group);
        System.out.println("[F1-CASSE] Groupe créé : " + groupNameUpper + " (ID: " + groupId + ")");
        return groupId;
    }

    /**
     * Create a group with specific ID (for loading from file)
     */
    public void createGroupWithId(String groupId, String groupName) {
        if (groupId == null || groupName == null) {
            return;
        }
        
        String groupNameUpper = groupName.trim().toUpperCase();
        
        AttributeGroup group = new AttributeGroup(groupId, groupNameUpper);
        groups.put(groupId, group);
        
        try {
            int id = Integer.parseInt(groupId.replace("group_", ""));
            if (id >= groupIdCounter.get()) {
                groupIdCounter.set(id + 1);
            }
        } catch (NumberFormatException e) {
            // Ignore
        }
    }

    // ============== READ ==============

    /**
     * Get a group by its ID
     */
    public AttributeGroup getGroup(String groupId) {
        return groups.get(groupId);
    }

    /**
     * Get a group by its NAME
     */
    public AttributeGroup getGroupByName(String groupName) {
        if (groupName == null) {
            return null;
        }
        String nameUpper = groupName.trim().toUpperCase();
        for (AttributeGroup group : groups.values()) {
            if (group.getGroupName().equalsIgnoreCase(nameUpper)) {
                return group;
            }
        }
        return null;
    }

    /**
     * Get all groups (in order)
     */
    public Collection<AttributeGroup> getAllGroups() {
        return groups.values();
    }

    /**
     * 🆕 Get all groups as ordered list
     */
    public List<AttributeGroup> getAllGroupsAsList() {
        return new ArrayList<>(groups.values());
    }

    /**
     * Get the group ID that contains a specific attribute (null if attribute is not grouped)
     */
    public String getGroupIdForAttribute(String attributeName) {
        for (AttributeGroup group : groups.values()) {
            if (group.containsAttribute(attributeName)) {
                return group.getGroupId();
            }
        }
        return null;
    }

    /**
     * Get the group that contains a specific attribute
     */
    public AttributeGroup getGroupForAttribute(String attributeName) {
        String groupId = getGroupIdForAttribute(attributeName);
        return groupId != null ? groups.get(groupId) : null;
    }

    /**
     * Check if a group exists
     */
    public boolean containsGroup(String groupId) {
        return groups.containsKey(groupId);
    }

    /**
     * Get number of groups
     */
    public int getGroupCount() {
        return groups.size();
    }

    /**
     * 🆕 Get position of a group (0-based index)
     */
    public int getGroupPosition(String groupId) {
        if (groupId == null) return -1;
        
        List<String> groupIds = new ArrayList<>(groups.keySet());
        return groupIds.indexOf(groupId);
    }

    // ============== UPDATE ==============

    /**
     * Update an existing group
     */
    public boolean updateGroup(AttributeGroup updatedGroup) {
        if (updatedGroup == null || !containsGroup(updatedGroup.getGroupId())) {
            return false;
        }
        groups.put(updatedGroup.getGroupId(), updatedGroup);
        return true;
    }

    /**
     * Rename a group
     */
    public boolean renameGroup(String groupId, String newName) {
        if (newName == null || newName.isEmpty()) {
            System.out.println("[F1-CASSE] Rename échoué: newName vide");
            return false;
        }
        
        AttributeGroup group = getGroup(groupId);
        if (group == null) {
            System.out.println("[F1-CASSE] Rename échoué: groupe " + groupId + " non trouvé");
            return false;
        }
        
        String newNameUpper = newName.trim().toUpperCase();
        
        if (!group.getGroupName().equals(newNameUpper) && groupNameExists(newNameUpper)) {
            System.out.println("[F1-CASSE] Rename échoué: '" + newNameUpper + "' existe déjà");
            return false;
        }
        
        System.out.println("[F1-CASSE] Renaming group: " + group.getGroupName() + " → " + newNameUpper);
        group.setGroupName(newNameUpper);
        return true;
    }

    /**
     * Add an attribute to a group
     */
    public boolean addAttributeToGroup(String groupId, String attributeName) {
        AttributeGroup group = getGroup(groupId);
        if (group == null) {
            return false;
        }
        return group.addAttribute(attributeName);
    }

    /**
     * Remove an attribute from a group
     */
    public boolean removeAttributeFromGroup(String groupId, String attributeName) {
        AttributeGroup group = getGroup(groupId);
        if (group == null) {
            return false;
        }
        return group.removeAttribute(attributeName);
    }

    /**
     * Toggle expanded state of a group (for UI)
     */
    public boolean toggleGroupExpansion(String groupId) {
        AttributeGroup group = getGroup(groupId);
        if (group == null) {
            return false;
        }
        group.setExpanded(!group.isExpanded());
        return true;
    }

    // ============== 🆕 GROUP REORDERING ==============

    /**
     * 🆕 Move a group to a new position
     * 
     * @param groupId ID of the group to move
     * @param newPosition Target position (0-based index)
     * @return true if successful, false otherwise
     */
    public boolean reorderGroup(String groupId, int newPosition) {
        if (groupId == null || !containsGroup(groupId)) {
            System.out.println("[REORDER] Group not found: " + groupId);
            return false;
        }

        List<String> groupIds = new ArrayList<>(groups.keySet());
        int currentPosition = groupIds.indexOf(groupId);
        
        if (currentPosition == -1) {
            System.out.println("[REORDER] Group not in list: " + groupId);
            return false;
        }

        // Clamp newPosition
        newPosition = Math.max(0, Math.min(newPosition, groupIds.size() - 1));
        
        if (currentPosition == newPosition) {
            return true; // Already at target position
        }

        System.out.println("[REORDER] Moving group " + groupId + " from pos " + 
                          currentPosition + " to pos " + newPosition);

        // Remove from current position
        groupIds.remove(currentPosition);
        
        // Insert at new position
        groupIds.add(newPosition, groupId);

        // Rebuild the map with new order
        Map<String, AttributeGroup> reorderedGroups = new LinkedHashMap<>();
        for (String id : groupIds) {
            reorderedGroups.put(id, groups.get(id));
        }

        // Replace the map
        groups.clear();
        groups.putAll(reorderedGroups);

        System.out.println("[REORDER] New order: " + groupIds);
        return true;
    }

    /**
     * 🆕 Move a group left (decrease position)
     */
    public boolean moveGroupLeft(String groupId) {
        int currentPos = getGroupPosition(groupId);
        if (currentPos <= 0) {
            return false; // Already at leftmost position
        }
        return reorderGroup(groupId, currentPos - 1);
    }

    /**
     * 🆕 Move a group right (increase position)
     */
    public boolean moveGroupRight(String groupId) {
        int currentPos = getGroupPosition(groupId);
        if (currentPos < 0 || currentPos >= groups.size() - 1) {
            return false; // Already at rightmost position
        }
        return reorderGroup(groupId, currentPos + 1);
    }

    // ============== 🆕 MOVE ATTRIBUTE BETWEEN GROUPS ==============

    /**
     * 🆕 Move an attribute from its current group to another group
     * 
     * @param attributeName Name of the attribute to move
     * @param targetGroupId ID of the target group (null = ungroup)
     * @return true if successful, false otherwise
     */
    public boolean moveAttributeToGroup(String attributeName, String targetGroupId) {
        if (attributeName == null || attributeName.isEmpty()) {
            System.out.println("[MOVE-ATTR] Invalid attribute name");
            return false;
        }

        // Find current group
        String currentGroupId = getGroupIdForAttribute(attributeName);
        
        // If target is null, just remove from current group (ungroup)
        if (targetGroupId == null) {
            if (currentGroupId != null) {
                return removeAttributeFromGroup(currentGroupId, attributeName);
            }
            return true; // Already ungrouped
        }

        // Check target group exists
        if (!containsGroup(targetGroupId)) {
            System.out.println("[MOVE-ATTR] Target group not found: " + targetGroupId);
            return false;
        }

        // If already in target group, nothing to do
        if (targetGroupId.equals(currentGroupId)) {
            System.out.println("[MOVE-ATTR] Attribute already in target group");
            return true;
        }

        System.out.println("[MOVE-ATTR] Moving '" + attributeName + "' from " + 
                          currentGroupId + " to " + targetGroupId);

        // Remove from current group if needed
        if (currentGroupId != null) {
            removeAttributeFromGroup(currentGroupId, attributeName);
        }

        // Add to target group
        return addAttributeToGroup(targetGroupId, attributeName);
    }

    // ============== DELETE ==============

    /**
     * Remove a group (ungroups its attributes)
     */
    public boolean removeGroup(String groupId) {
        return groups.remove(groupId) != null;
    }

    /**
     * Remove all groups
     */
    public void clear() {
        groups.clear();
        groupIdCounter.set(0);
    }

    // ============== UTILITY ==============

    /**
     * Check if an attribute is in any group
     */
    public boolean isAttributeGrouped(String attributeName) {
        return getGroupIdForAttribute(attributeName) != null;
    }

    /**
     * Get all attributes that are NOT in any group
     */
    public Collection<String> getUngroupedAttributes() {
        List<String> ungrouped = new ArrayList<>();
        for (String attr : context.getAttributes()) {
            if (!isAttributeGrouped(attr)) {
                ungrouped.add(attr);
            }
        }
        return ungrouped;
    }

    /**
     * Check if a group name exists
     */
    public boolean groupNameExists(String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            return false;
        }
        String nameUpper = groupName.trim().toUpperCase();
        for (AttributeGroup group : groups.values()) {
            if (group.getGroupName().equalsIgnoreCase(nameUpper)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all group names
     */
    public java.util.Set<String> getAllGroupNames() {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (AttributeGroup group : groups.values()) {
            names.add(group.getGroupName());
        }
        return names;
    }

    // ============== CLONE (FIX UNDO/REDO) ==============

    /**
     * Deep clone of the manager (for undo/redo)
     */
    @Override
    public AttributeGroupManager clone() {
        AttributeGroupManager cloned = new AttributeGroupManager(this.context);
        
        cloned.groupIdCounter.set(this.groupIdCounter.get());
        
        // Clone all groups in order (LinkedHashMap preserves order)
        for (Map.Entry<String, AttributeGroup> entry : this.groups.entrySet()) {
            String groupId = entry.getKey();
            AttributeGroup originalGroup = entry.getValue();
            
            AttributeGroup clonedGroup = new AttributeGroup(
                originalGroup.getGroupId(),
                originalGroup.getGroupName()
            );
            
            for (String attrName : originalGroup.getAttributeNames()) {
                clonedGroup.addAttribute(attrName);
            }
            
            clonedGroup.setExpanded(originalGroup.isExpanded());
            
            cloned.groups.put(groupId, clonedGroup);
        }
        
        return cloned;
    }

    @Override
    public String toString() {
        return "AttributeGroupManager{groups=" + groups.size() + ", order=" + 
               new ArrayList<>(groups.keySet()) + "}";
    }
}