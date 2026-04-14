package fcatools.conexpng.model;
import fcatools.conexpng.model.AttributeGroupManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import de.tudresden.inf.tcs.fcaapi.Concept;
import de.tudresden.inf.tcs.fcaapi.FCAImplication;
import de.tudresden.inf.tcs.fcaapi.exception.IllegalAttributeException;
import de.tudresden.inf.tcs.fcaapi.exception.IllegalObjectException;
import de.tudresden.inf.tcs.fcaapi.utils.IndexedSet;
import de.tudresden.inf.tcs.fcalib.FullObject;
import de.tudresden.inf.tcs.fcalib.Implication;
import de.tudresden.inf.tcs.fcalib.ImplicationSet;
import de.tudresden.inf.tcs.fcalib.utils.ListSet;
import de.tudresden.inf.tcs.fcalib.utils.ListSet;
import de.tudresden.inf.tcs.fcaapi.utils.IndexedSet;
/**
 * A specialization of FormalContext<String,String> with the aim to remove the
 * verbose repetition of <String,String>. Plus, adds a couple of useful methods.
 * Due to the API of FormalContext<String,String> the here implemented methods
 * are extremely inefficient.
 */


public class FormalContext extends de.tudresden.inf.tcs.fcalib.FormalContext<String, String> {

    protected HashMap<String, SortedSet<String>> objectsOfAttribute = new HashMap<>();
    private ArrayList<String> dontConsideredAttr = new ArrayList<>();
    private ArrayList<FullObject<String, String>> dontConsideredObj = new ArrayList<>();

    // NOUVEAU pour la fonctionnalité (F1): Groupes d'Attributs
    private AttributeGroupManager attributeGroupManager;
    
    @Override
    public boolean addAttribute(String attribute) throws IllegalAttributeException {
        if (super.addAttribute(attribute)) {
            objectsOfAttribute.put(attribute, new TreeSet<String>());
            return true;
        } else
            return false;
    }

    @Override
    public boolean addAttributeToObject(String attribute, String id) throws IllegalAttributeException,
            IllegalObjectException {
        if (super.addAttributeToObject(attribute, id)) {
            SortedSet<String> objects = objectsOfAttribute.get(attribute);
            if (objects != null)
                objects.add(id);
            return true;
        }
        return false;
    }

    @Override
    public boolean addObject(FullObject<String, String> arg0) throws IllegalObjectException {
        if (super.addObject(arg0)) {
            for (String attribute : arg0.getDescription().getAttributes()) {
                objectsOfAttribute.get(attribute).add(arg0.getIdentifier());
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean removeAttributeFromObject(String attribute, String id) throws IllegalAttributeException,
            IllegalObjectException {
        if (super.removeAttributeFromObject(attribute, id)) {
            SortedSet<String> objects = objectsOfAttribute.get(attribute);
            if (objects != null)
                objects.remove(id);
            return true;
        }
        return false;
    }

    @Override
    public boolean removeObject(String id) throws IllegalObjectException {
        return removeObject(getObject(id));
    }

    @Override
    public boolean removeObject(FullObject<String, String> object) throws IllegalObjectException {
        if (super.removeObject(object)) {
            for (String attribute : object.getDescription().getAttributes()) {
                objectsOfAttribute.get(attribute).remove(object.getIdentifier());
            }
        }
        return false;
    }

    public FormalContext() {
        super();
        objectsOfAttribute = new HashMap<>();
        // F1 : Initialiser le gestionnaire de groupes
        this.attributeGroupManager = new AttributeGroupManager(this);
    }

    public FormalContext(int objectsCount, int attributesCount) {
        super();
        objectsOfAttribute = new HashMap<>();
        // F1 : Initialiser le gestionnaire de groupes
        this.attributeGroupManager = new AttributeGroupManager(this);
        
        for (int i = 0; i < attributesCount; i++) {
            addAttribute("attr" + i);
        }
        for (int i = 0; i < objectsCount; i++) {
            try {
                addObject(new FullObject<String, String>("obj" + i));
            } catch (IllegalObjectException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Set<Concept<String, FullObject<String, String>>> getConcepts() {
        ListSet<Concept<String, FullObject<String, String>>> conceptLattice = new ListSet<Concept<String, FullObject<String, String>>>();

        HashMap<String, Set<String>> extentPerAttr = new HashMap<String, Set<String>>();
        /*
         * Step 1: Initialize a list of concept extents. To begin with, write
         * for each attribute m # M the attribute extent {m}$ to this list (if
         * not already present).
         */
        for (String s : this.getAttributes()) {
            TreeSet<String> set = new TreeSet<String>();
            for (FullObject<String, String> f : this.getObjects()) {
                if (f.getDescription().getAttributes().contains(s)) {
                    set.add(f.getIdentifier());
                }
            }
            extentPerAttr.put(s, set);
        }

        /*
         * Step 2: For any two sets in this list, compute their intersection. If
         * the result is a set that is not yet in the list, then extend the list
         * by this set. With the extended list, continue to build all pairwise
         * intersections.
         */
        HashMap<String, Set<String>> temp = new HashMap<String, Set<String>>();
        for (String s : extentPerAttr.keySet()) {
            for (String t : extentPerAttr.keySet()) {
                if (!s.equals(t)) {
                    Set<String> result = this.intersection(extentPerAttr.get(s), extentPerAttr.get(t));
                    if (!extentPerAttr.values().contains(result)) {
                        if (!temp.containsValue(result)) {
                            temp.put(s + ", " + t, result);
                        }
                    }
                }
            }
        }
        extentPerAttr.putAll(temp);

        /*
         * Step 3: If for any two sets of the list their intersection is also in
         * the list, then extend the list by the set G (provided it is not yet
         * contained in the list). The list then contains all concept extents
         * (and nothing else).
         */
        boolean notcontained = false;
        for (String s : extentPerAttr.keySet()) {
            if (notcontained)
                break;
            for (String t : extentPerAttr.keySet()) {
                if (!s.equals(t)) {
                    Set<String> result = this.intersection(extentPerAttr.get(s), extentPerAttr.get(t));
                    if (!extentPerAttr.values().contains(result)) {
                        notcontained = true;
                        break;
                    }
                }
            }
        }
        if (!notcontained) {
            TreeSet<String> set = new TreeSet<String>();
            for (FullObject<String, String> f : this.getObjects()) {
                set.add(f.getIdentifier());
            }
            if (!extentPerAttr.values().contains(set))
                extentPerAttr.put("", set);
        }

        /*
         * Step 4: For every concept extent A in the list compute the
         * corresponding intent A' to obtain a list of all formal concepts
         * (A,A') of (G,M, I).
         */
        HashSet<Set<String>> extents = new HashSet<Set<String>>();
        for (Set<String> e : extentPerAttr.values()) {
            if (!extents.contains(e))
                extents.add(e);
        }
        for (Set<String> e : extents) {
            TreeSet<String> intents = new TreeSet<String>();
            int count = 0;
            Concept<String, FullObject<String, String>> c = new LatticeConcept();
            if (e.isEmpty()) {
                intents.addAll(getAttributes());
            } else
                for (FullObject<String, String> i : this.getObjects()) {
                    if (e.contains(i.getIdentifier().toString())) {
                        TreeSet<String> prev = sort(i.getDescription().getAttributes());
                        if (count > 0) {
                            intents = intersection(prev, intents);
                        } else {
                            intents = prev;
                        }
                        count++;
                        c.getExtent().add(i);
                    }
                }
            // concepts.put(e, intents);
            for (String s : intents) {
                c.getIntent().add(s);
            }
            conceptLattice.add(c);
        }
        return conceptLattice;
    }

    public Set<Concept<String, FullObject<String, String>>> getConceptsWithoutConsideredElements() {
        ListSet<Concept<String, FullObject<String, String>>> conceptLattice = new ListSet<Concept<String, FullObject<String, String>>>();

        HashMap<String, Set<String>> extentPerAttr = new HashMap<String, Set<String>>();
        /*
         * Step 1: Initialize a list of concept extents. To begin with, write
         * for each attribute m # M the attribute extent {m}$ to this list (if
         * not already present).
         */
        for (String s : this.getAttributes()) {
            if (!dontConsideredAttr.contains(s)) {
                TreeSet<String> set = new TreeSet<String>();
                for (FullObject<String, String> f : this.getObjects()) {
                    if (f.getDescription().getAttributes().contains(s) && (!dontConsideredObj.contains(f))) {
                        set.add(f.getIdentifier());
                    }
                }
                extentPerAttr.put(s, set);
            }
        }

        /*
         * Step 2: For any two sets in this list, compute their intersection. If
         * the result is a set that is not yet in the list, then extend the list
         * by this set. With the extended list, continue to build all pairwise
         * intersections.
         */
        HashMap<String, Set<String>> temp = new HashMap<String, Set<String>>();
        for (String s : extentPerAttr.keySet()) {
            for (String t : extentPerAttr.keySet()) {
                if (!s.equals(t)) {
                    Set<String> result = this.intersection(extentPerAttr.get(s), extentPerAttr.get(t));
                    if (!extentPerAttr.values().contains(result)) {
                        if (!temp.containsValue(result)) {
                            temp.put(s + ", " + t, result);
                        }
                    }
                }
            }
        }
        extentPerAttr.putAll(temp);

        /*
         * Step 3: If for any two sets of the list their intersection is also in
         * the list, then extend the list by the set G (provided it is not yet
         * contained in the list). The list then contains all concept extents
         * (and nothing else).
         */
        boolean notcontained = false;
        for (String s : extentPerAttr.keySet()) {
            if (notcontained)
                break;
            for (String t : extentPerAttr.keySet()) {
                if (!s.equals(t)) {
                    Set<String> result = this.intersection(extentPerAttr.get(s), extentPerAttr.get(t));
                    if (!extentPerAttr.values().contains(result)) {
                        notcontained = true;
                        break;
                    }
                }
            }
        }
        if (!notcontained) {
            TreeSet<String> set = new TreeSet<String>();
            for (FullObject<String, String> f : this.getObjects()) {
                set.add(f.getIdentifier());
            }
            if (!extentPerAttr.values().contains(set))
                extentPerAttr.put("", set);
        }

        /*
         * Step 4: For every concept extent A in the list compute the
         * corresponding intent A' to obtain a list of all formal concepts
         * (A,A') of (G,M, I).
         */

        HashSet<Set<String>> extents = new HashSet<Set<String>>();
        for (Set<String> e : extentPerAttr.values()) {
            if (!extents.contains(e))
                extents.add(e);
        }
        for (Set<String> e : extents) {
            TreeSet<String> intents = new TreeSet<String>();
            int count = 0;
            Concept<String, FullObject<String, String>> c = new LatticeConcept();
            if (e.isEmpty()) {
                intents.addAll(getAttributes());
            } else
                for (FullObject<String, String> i : this.getObjects()) {
                    if (!dontConsideredObj.contains(i)) {
                        if (e.contains(i.getIdentifier().toString())) {
                            TreeSet<String> prev = sort(i.getDescription().getAttributes());
                            if (count > 0) {
                                intents = intersection(prev, intents);
                            } else {
                                intents = prev;
                            }
                            count++;
                            c.getExtent().add(i);
                        }
                    }
                }
            // concepts.put(e, intents);
            for (String s : intents) {
                if (!dontConsideredAttr.contains(s))
                    c.getIntent().add(s);
            }
            conceptLattice.add(c);
        }
        return conceptLattice;
    }

    public int supportCount(Set<String> attributes) {
        if (attributes.isEmpty())
            return objects.size();
        int mincount = Integer.MAX_VALUE;
        String attributeWithMincount = "";
        // search for the attribute with the fewest objects
        for (String string : attributes) {
            if (objectsOfAttribute.get(string).size() < mincount) {
                mincount = objectsOfAttribute.get(string).size();
                attributeWithMincount = string;
            }
        }
        int count = 0;
        boolean notfound;
        // search the other attributes only in these objects
        for (String obj : objectsOfAttribute.get(attributeWithMincount)) {
            notfound = false;
            for (String att : attributes) {
                if (!objectHasAttribute(getObject(obj), att)) {
                    notfound = true;
                    break;
                }
            }
            if (!notfound)
                count++;
        }
        return count;

    }

    @Override
    public Set<FCAImplication<String>> getStemBase() {
        // de.tudresden.inf.tcs.fcalib.ImplicationSet<String> doesn't return the
        // implications, so we need this result-variable, maybe we should modify
        // ImplicationSet
        IndexedSet<FCAImplication<String>> result = new ListSet<>();

        ImplicationSet<String> impl = new ImplicationSet<>(this);

        // Next-Closure

        Set<String> A = new ListSet<>();

        while (!A.equals(getAttributes())) {
            A = impl.nextClosure(A);
            if (A == null)
                return Collections.emptySet();
            if (!A.equals(doublePrime(A))) {
                Implication<String> im = new Implication<>(A, doublePrime(A));
                impl.add(im);
                result.add(im);
            }
        }
        // remove redundant items in the conclusion
        for (FCAImplication<String> fcaImplication : result) {
            fcaImplication.getConclusion().removeAll(fcaImplication.getPremise());
        }

        return result;
    }

    @Override
    public Set<FCAImplication<String>> getDuquenneGuiguesBase() {
        return getStemBase();
    }

    /**
     * Returns objects of this context.
     * 
     * @return objects of this context
     */
    public IndexedSet<FullObject<String, String>> getObjects() {
        return objects;
    }

    /**
     * Removes given object only without removing attributes.
     * 
     * @param o
     *            object to remove
     */
    public void removeObjectOnly(FullObject<String, String> o) {
        objects.remove(o);
    }

    public void transpose() {
        IndexedSet<FullObject<String, String>> newObjects = new ListSet<>();
        IndexedSet<String> newAttributes = new ListSet<>();
        for (String attribute : getAttributes()) {
            IndexedSet<String> allObjectsForAttribute = new ListSet<>();
            for (FullObject<String, String> object : objects) {
                if (objectHasAttribute(object, attribute))
                    allObjectsForAttribute.add(object.getIdentifier());
            }
            newObjects.add(new FullObject<>(attribute, allObjectsForAttribute));
        }
        for (FullObject<String, String> object : objects) {
            newAttributes.add(object.getIdentifier());
        }

        objects = newObjects;
        // Why can I access objects directly but not attributes? (I'm
        // questioning the API-decision)
        getAttributes().clear();
        objectsOfAttribute.clear();
        for (String attribute : newAttributes) {
            getAttributes().add(attribute);
            objectsOfAttribute.put(attribute, new TreeSet<String>());
        }
        for (FullObject<String, String> object : objects) {
            for (String attribute : object.getDescription().getAttributes()) {
                objectsOfAttribute.get(attribute).add(object.getIdentifier());
            }
        }
    }

    public void toggleAttributeForObject(String attribute, String objectID) {
        if (objectHasAttribute(getObject(objectID), attribute)) {
            try {
                removeAttributeFromObject(attribute, objectID);
            } catch (IllegalObjectException e) {
                e.printStackTrace();
            }
        } else {
            try {
                this.addAttributeToObject(attribute, objectID);
            } catch (IllegalObjectException e) {
                e.printStackTrace();
            }
        }
    }

    public void invert(int objectStartIndex, int objectEndIndex, int attributeStartIndex, int attributeEndIndex) {
        for (int i = objectStartIndex; i < objectEndIndex; i++) {
            for (int j = attributeStartIndex; j < attributeEndIndex; j++) {
                String objectID = getObjectAtIndex(i).getIdentifier();
                String attribute = getAttributeAtIndex(j);
                toggleAttributeForObject(attribute, objectID);
            }
        }
    }

    public void invert() {
        invert(0, getObjectCount() - 1, 0, getAttributeCount() - 1);
    }

    public void clear(int objectStartIndex, int objectEndIndex, int attributeStartIndex, int attributeEndIndex) {
        for (int i = objectStartIndex; i < objectEndIndex; i++) {
            for (int j = attributeStartIndex; j < attributeEndIndex; j++) {
                FullObject<String, String> object = getObjectAtIndex(i);
                String attribute = getAttributeAtIndex(j);
                if (objectHasAttribute(object, attribute)) {
                    try {
                        removeAttributeFromObject(attribute, object.getIdentifier());
                    } catch (IllegalObjectException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void clear() {
        clear(0, getObjectCount() - 1, 0, getAttributeCount() - 1);
    }

    public void fill(int objectStartIndex, int objectEndIndex, int attributeStartIndex, int attributeEndIndex) {
        for (int i = objectStartIndex; i < objectEndIndex; i++) {
            for (int j = attributeStartIndex; j < attributeEndIndex; j++) {
                FullObject<String, String> object = getObjectAtIndex(i);
                String attribute = getAttributeAtIndex(j);
                if (!objectHasAttribute(object, attribute)) {
                    try {
                        addAttributeToObject(attribute, object.getIdentifier());
                    } catch (IllegalObjectException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void fill() {
        fill(0, getObjectCount() - 1, 0, getAttributeCount() - 1);
    }

    public void renameAttribute(String oldName, String newName) {
        IndexedSet<String> newAttributes = new ListSet<>();
        IndexedSet<FullObject<String, String>> filteredObjects = new ListSet<>();
        for (FullObject<String, String> object : objects) {
            if (objectHasAttribute(object, oldName)) {
                filteredObjects.add(object);
                try {
                    removeAttributeFromObject(oldName, object.getIdentifier());
                } catch (IllegalObjectException e) {
                    e.printStackTrace();
                }
            }
        }
        for (String attribute : getAttributes()) {
            if (attribute.equals(oldName)) {
                newAttributes.add(newName);
            } else {
                newAttributes.add(attribute);
            }
        }
        getAttributes().clear();
        for (String attribute : newAttributes) {
            getAttributes().add(attribute);
        }
        for (FullObject<String, String> object : filteredObjects) {
            try {
                addAttributeToObject(newName, object.getIdentifier());
            } catch (IllegalObjectException e) {
                e.printStackTrace();
            }
        }
    }

    public void renameObject(String oldName, String newName) {
        IndexedSet<FullObject<String, String>> newObjects = new ListSet<>();
        // IndexedSet<String> filteredAttributes = new ListSet<>();
        for (FullObject<String, String> object : objects) {
            if (object.getIdentifier().equals(oldName)) {
                newObjects.add(new FullObject<String, String>(newName, getAttributesForObject(oldName)));
            } else {
                newObjects.add(object);
            }
        }
        objects = newObjects;
        for (SortedSet<String> objects : objectsOfAttribute.values()) {
            if (objects.contains(oldName)) {
                objects.remove(oldName);
                objects.add(newName);
            }
        }
    }

    public boolean existsAttributeAlready(String name) {
        for (String attribute : getAttributes()) {
            if (attribute.equals(name))
                return true;
        }
        return false;
    }

    public boolean existsObjectAlready(String name) {
        for (FullObject<String, String> object : objects) {
            if (object.getIdentifier().equals(name))
                return true;
        }
        return false;
    }

    public Set<String> getAttributesForObject(String objectID) {
        Set<String> attributes = new HashSet<>();
        FullObject<String, String> object = getObject(objectID);
        for (String attribute : getAttributes()) {
            if (objectHasAttribute(object, attribute)) {
                attributes.add(attribute);
            }
        }
        return attributes;
    }

    public void removeAttribute(String attribute) {
        IndexedSet<String> newAttributes = new ListSet<>();
        for (FullObject<String, String> object : objects) {
            if (objectHasAttribute(object, attribute)) {
                try {
                    removeAttributeFromObject(attribute, object.getIdentifier());
                } catch (IllegalObjectException e) {
                    e.printStackTrace();
                }
            }
        }
        for (String attr : getAttributes()) {
            if (attr.equals(attribute)) {
            } else {
                newAttributes.add(attr);
            }
        }
        getAttributes().clear();
        for (String attr : newAttributes) {
            getAttributes().add(attr);
        }
    }

    // Should not be used outside the context editor
    public void removeAttributeInternal(String attribute) {
        IndexedSet<String> newAttributes = new ListSet<>();
        for (String attr : getAttributes()) {
            if (attr.equals(attribute)) {
            } else {
                newAttributes.add(attr);
            }
        }
        getAttributes().clear();
        for (String attr : newAttributes) {
            getAttributes().add(attr);
        }
    }

    public void addObjectAt(FullObject<String, String> object, int i) {
        IndexedSet<FullObject<String, String>> newObjects = new ListSet<>();
        for (int j = 0; j < getObjectCount(); j++) {
            if (j == i)
                newObjects.add(object);
            newObjects.add(getObjectAtIndex(j));
        }
        if (i == getObjectCount())
            newObjects.add(object);
        objects = newObjects;
    }

    public void addAttributeAt(String attribute, int i) {
        IndexedSet<String> newAttributes = new ListSet<>();
        for (int j = 0; j < getAttributeCount(); j++) {
            if (j == i)
                newAttributes.add(attribute);
            newAttributes.add(getAttributeAtIndex(j));
        }
        if (i == getAttributeCount())
            newAttributes.add(attribute);
        getAttributes().clear();
        for (String attr : newAttributes) {
            getAttributes().add(attr);
        }
        objectsOfAttribute.put(attribute, new TreeSet<String>());
    }

    public TreeSet<String> intersection(Set<String> firstSet, Set<String> secondSet) {
        TreeSet<String> result = new TreeSet<String>();
        for (String s : firstSet) {
            for (String t : secondSet) {
                if (s == t) {
                    result.add(s);
                }
            }
        }
        return result;
    }

    public TreeSet<String> sort(Set<String> sortable) {
        TreeSet<String> result = new TreeSet<String>();
        for (String s : sortable) {
            result.add(s);
        }
        return result;
    }

    /**
     * Set Attribute which don't be consider by lattice computation.
     * 
     * @param attr
     */
    public void dontConsiderAttribute(String attr) {
        this.dontConsideredAttr.add(attr);
    }

    /**
     * Set Attribute which has to be reconsider by lattice computation.
     * 
     * @param attr
     */
    public void considerAttribute(String attr) {
        this.dontConsideredAttr.remove(attr);
    }

    /**
     * Set Object which don't be consider by lattice computation.
     * 
     * @param obj
     */
    public void dontConsiderObject(FullObject<String, String> obj) {
        this.dontConsideredObj.add(obj);
    }

    /**
     * Set Object which has to be reconsider by lattice computation.
     * 
     * @param obj
     */
    public void considerObject(FullObject<String, String> obj) {
        this.dontConsideredObj.remove(obj);
    }

    public void clearConsidered() {
        dontConsideredAttr.clear();
        dontConsideredObj.clear();
    }

    public ArrayList<String> getDontConsideredAttr() {
        return dontConsideredAttr;
    }

    public ArrayList<FullObject<String, String>> getDontConsideredObj() {
        return dontConsideredObj;
    }

 // ═════════════════════════════════════════════════════════════
    // FONCTIONNALITÉ 1: GROUPES D'ATTRIBUTS
    // ═════════════════════════════════════════════════════════════
 
    /**
     * Get the attribute group manager
     */
    public AttributeGroupManager getAttributeGroupManager() {
        return attributeGroupManager;
    }
 
    /**
     * Create a new attribute group with the given name and attributes
     * @param groupName Name for the group (e.g., "Conditions Météo")
     * @param attributeNames Set of attribute names to add to the group
     * @return The generated group ID (e.g., "group_0")
     */
    public String createAttributeGroup(String groupName, java.util.Set<String> attributeNames) {
        String groupId = attributeGroupManager.createGroup(groupName);
        if (groupId == null) {
            return null;
        }
        for (String attr : attributeNames) {
            attributeGroupManager.addAttributeToGroup(groupId, attr);
        }
        return groupId;
    }
 
    /**
     * Remove an attribute group (ungroups its attributes)
     */
    public boolean removeAttributeGroup(String groupId) {
        return attributeGroupManager.removeGroup(groupId);
    }
 
    /**
     * Get all attributes belonging to a specific group
     */
    public java.util.List<String> getAttributesInGroup(String groupId) {
        AttributeGroup group = attributeGroupManager.getGroup(groupId);
        if (group == null) {
            return new java.util.ArrayList<>();
        }
        return group.getAttributeNames();
    }
 
    /**
     * Check if an attribute is in any group
     */
    public boolean isAttributeInGroup(String attributeName) {
        return attributeGroupManager.isAttributeGrouped(attributeName);
    }
 
    /**
     * Get the group containing a specific attribute
     */
    public AttributeGroup getGroupForAttribute(String attributeName) {
        return attributeGroupManager.getGroupForAttribute(attributeName);
    }
 
    /**
     * Get the group name for an attribute (null if attribute is not grouped)
     */
    public String getGroupNameForAttribute(String attributeName) {
        AttributeGroup group = getGroupForAttribute(attributeName);
        return group != null ? group.getGroupName() : null;
    }
 
    /**
     * Get all groups
     */
    public java.util.Collection<AttributeGroup> getAllAttributeGroups() {
        return attributeGroupManager.getAllGroups();
    }
 
    /**
     * Generate concepts for a specific attribute group
     * This method filters the concepts to only include attributes from the specified group
     * 
     * @param groupId The group ID
     * @return A set of concepts considering only attributes in the group
     */
    public Set<Concept<String, FullObject<String, String>>> getConceptsForGroup(String groupId) {
        AttributeGroup group = attributeGroupManager.getGroup(groupId);
        if (group == null) {
            return new java.util.HashSet<>();
        }
 
        // Get all concepts first
        Set<Concept<String, FullObject<String, String>>> allConcepts = getConcepts();
 
        // Filter concepts to only include attributes from this group
        Set<Concept<String, FullObject<String, String>>> filteredConcepts = new java.util.HashSet<>();
        
        for (Concept<String, FullObject<String, String>> concept : allConcepts) {
            // Create a new concept with only the attributes from the group
            LatticeConcept filtered = new LatticeConcept();
            filtered.getExtent().addAll(concept.getExtent());
            
            // Only add attributes that belong to this group
            for (String attr : concept.getIntent()) {
                if (group.containsAttribute(attr)) {
                    filtered.getIntent().add(attr);
                }
            }
            
            filteredConcepts.add(filtered);
        }
 
        return filteredConcepts;
    }
 
    /**
     * Get attributes that are NOT in any group
     */
    public java.util.Collection<String> getUngroupedAttributes() {
        return attributeGroupManager.getUngroupedAttributes();
    }
 
    /**
     * Get the number of attribute groups
     */
    public int getAttributeGroupCount() {
        return attributeGroupManager.getGroupCount();
    }   
    
    /**
     * (F1) ÉTAPE 2 : Réorganiser les attributs pour mettre les groupes ensemble
     * 
     * Après création d'un groupe, cette méthode déplace les attributs du groupe
     * pour qu'ils soient côte à côte (facilite l'affichage matriciel)
     * 
     * Exemple :
     * Avant : [female, adult, juvenile, male]
     * Groupes : gender(female,male), age(adult,juvenile)
     * Après : [female, male, adult, juvenile]
     */
    public void reorganizeAttributesForGroups() {
        try {
            java.util.List<String> newOrder = new java.util.ArrayList<>();
            java.util.Set<String> processed = new java.util.HashSet<>();
            
            // Parcourir les groupes et ajouter leurs attributs ensemble
            for (AttributeGroup group : attributeGroupManager.getAllGroups()) {
                for (String attr : group.getAttributeNames()) {
                    if (!processed.contains(attr)) {
                        newOrder.add(attr);
                        processed.add(attr);
                    }
                }
            }
            
            // Ajouter les attributs non groupés à la fin
            for (String attr : getAttributes()) {
                if (!processed.contains(attr)) {
                    newOrder.add(attr);
                }
            }
            
            // Réorganiser les attributs dans le contexte
            reorderAttributes(newOrder);
            
            System.out.println("[F1] Attributs réorganisés : " + newOrder);
            
        } catch (Exception e) {
            System.err.println("[F1] Erreur réorganisation : " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * (F1) Helper : Réorganiser les attributs selon un nouvel ordre
     */
    private void reorderAttributes(java.util.List<String> newOrder) {
        IndexedSet<String> newAttributes = new ListSet<>();
        
        // Ajouter dans le nouvel ordre
        for (String attr : newOrder) {
            if (getAttributes().contains(attr)) {
                newAttributes.add(attr);
            }
        }
        
        // Remplacer les attributs
        getAttributes().clear();
        for (String attr : newAttributes) {
            getAttributes().add(attr);
        }
    }
}
