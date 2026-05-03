package fcatools.conexpng.model;
import fcatools.conexpng.model.AttributeGroupManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
 * 
 * (F1) MODIFIÉ : Auto-assigne les attributs orphelins à des groupes portant leur nom en MAJUSCULES
 * ✅ FIX CASSE : Force minuscule pour attributs/objets, MAJUSCULE pour groupes
 */


public class FormalContext extends de.tudresden.inf.tcs.fcalib.FormalContext<String, String> {

    protected HashMap<String, SortedSet<String>> objectsOfAttribute = new HashMap<>();
    private ArrayList<String> dontConsideredAttr = new ArrayList<>();
    private ArrayList<FullObject<String, String>> dontConsideredObj = new ArrayList<>();

    // NOUVEAU pour la fonctionnalité (F1): Groupes d'Attributs
    private AttributeGroupManager attributeGroupManager;
    
    /**
     * Map pour stocker les groupes d'objets après un transpose.
     * 
     * Key = Nom de l'objet (ex: "female")
     * Value = Nom du groupe (ex: "GENDER")
     * 
     * Cette map est utilisée pour afficher les groupes verticalement
     * dans la colonne 0 après un transpose.
     */
    private Map<String, String> objectToGroupMap = new HashMap<>();
    
    @Override
    public boolean addAttribute(String attribute) throws IllegalAttributeException {
        if (attribute == null || attribute.isEmpty()) return false;
        
        // ✅ FIX CASSE : Force minuscule pour les attributs
        String attrLower = attribute.toLowerCase();
        
        // ✅ FIX UNICITÉ : Vérifier si existe déjà (insensible à la casse)
        if (existsAttributeAlready(attrLower)) {
            System.out.println("[F1-CASSE] Attribut '" + attrLower + "' existe déjà, ignored");
            return false;
        }
        
        if (super.addAttribute(attrLower)) {
            objectsOfAttribute.put(attrLower, new TreeSet<String>());
            System.out.println("[F1-CASSE] Attribut ajouté : " + attrLower);
            return true;
        } else
            return false;
    }

    @Override
    public boolean addAttributeToObject(String attribute, String id) throws IllegalAttributeException,
            IllegalObjectException {
        // Force minuscule
        String attrLower = attribute != null ? attribute.toLowerCase() : attribute;
        
        if (super.addAttributeToObject(attrLower, id)) {
            SortedSet<String> objects = objectsOfAttribute.get(attrLower);
            if (objects != null)
                objects.add(id);
            return true;
        }
        return false;
    }

    @Override
    public boolean addObject(FullObject<String, String> arg0) throws IllegalObjectException {
        if (arg0 == null) return false;
        
        // ✅ FIX CASSE : Force minuscule pour les objets
        String objNameLower = arg0.getIdentifier().toLowerCase();
        FullObject<String, String> objLower = new FullObject<>(objNameLower, arg0.getDescription().getAttributes());
        
        // ✅ FIX UNICITÉ : Vérifier si existe déjà
        if (existsObjectAlready(objNameLower)) {
            System.out.println("[F1-CASSE] Objet '" + objNameLower + "' existe déjà, ignored");
            return false;
        }
        
        if (super.addObject(objLower)) {
            for (String attribute : objLower.getDescription().getAttributes()) {
                objectsOfAttribute.get(attribute).add(objNameLower);
            }
            System.out.println("[F1-CASSE] Objet ajouté : " + objNameLower);
            return true;
        }
        return false;
    }

    @Override
    public boolean removeAttributeFromObject(String attribute, String id) throws IllegalAttributeException,
            IllegalObjectException {
        String attrLower = attribute != null ? attribute.toLowerCase() : attribute;
        
        if (super.removeAttributeFromObject(attrLower, id)) {
            SortedSet<String> objects = objectsOfAttribute.get(attrLower);
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
        System.out.println("\n[TRANSPOSE] ========== DÉBUT DU TRANSPOSE ==========");
        
        // ÉTAPE 1 : Déterminer l'état AVANT transpose
        // ─────────────────────────────────────────────
        boolean hasAttributeGroups = attributeGroupManager.getGroupCount() > 0;
        boolean hasObjectGroups = !objectToGroupMap.isEmpty();
        
        System.out.println("[TRANSPOSE] État avant:");
        System.out.println("  - Groupes d'attributs : " + attributeGroupManager.getGroupCount());
        System.out.println("  - Groupes d'objets : " + objectToGroupMap.size());
        
        // ÉTAPE 2 : Sauvegarder les groupes d'attributs ACTUELS
        // ──────────────────────────────────────────────────────
        Map<String, Set<String>> savedAttributeGroups = new HashMap<>();
        if (hasAttributeGroups) {
            for (AttributeGroup group : getAllAttributeGroups()) {
                Set<String> attrs = new HashSet<>(group.getAttributeNames());
                savedAttributeGroups.put(group.getGroupName(), attrs);
                System.out.println("[TRANSPOSE] Groupe d'attribut sauvegardé: " + group.getGroupName() + " = " + attrs);
            }
        }
        
        // ÉTAPE 3 : Sauvegarder les groupes d'objets ACTUELS
        // ────────────────────────────────────────────────────
        Map<String, String> savedObjectGroups = new HashMap<>(objectToGroupMap);
        if (hasObjectGroups) {
            System.out.println("[TRANSPOSE] Groupes d'objets sauvegardés: " + savedObjectGroups);
        }
        
        // ÉTAPE 4 : Faire le transpose NORMAL (inversion lignes/colonnes)
        // ──────────────────────────────────────────────────────────────
        System.out.println("[TRANSPOSE] Exécution du transpose (inversion lignes/colonnes)");
        IndexedSet<FullObject<String, String>> newObjects = new ListSet<>();
        IndexedSet<String> newAttributes = new ListSet<>();
        
        // Les anciens attributs deviennent les nouveaux objets
        for (String attribute : getAttributes()) {
            IndexedSet<String> allObjectsForAttribute = new ListSet<>();
            for (FullObject<String, String> object : objects) {
                if (objectHasAttribute(object, attribute))
                    allObjectsForAttribute.add(object.getIdentifier());
            }
            newObjects.add(new FullObject<>(attribute, allObjectsForAttribute));
        }
        
        // Les anciens objets deviennent les nouveaux attributs
        for (FullObject<String, String> object : objects) {
            newAttributes.add(object.getIdentifier());
        }
        
        // Remplacer les données
        objects = newObjects;
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
        
        System.out.println("[TRANSPOSE] Transpose inversion complète");
        System.out.println("  - Nouveaux objets : " + getObjectCount());
        System.out.println("  - Nouveaux attributs : " + getAttributeCount());
        
        // ÉTAPE 5 : Mapper les anciens groupes aux nouvelles positions
        // ────────────────────────────────────────────────────────────
        
        // Nettoyer les groupes actuels
        attributeGroupManager.clear();
        objectToGroupMap.clear();
        
        // CAS A : On avait des groupes d'ATTRIBUTS avant → ils deviennent des groupes d'OBJETS
        if (hasAttributeGroups) {
            System.out.println("[TRANSPOSE] Conversion: groupes d'attributs → groupes d'objets");
            for (Map.Entry<String, Set<String>> entry : savedAttributeGroups.entrySet()) {
                String groupName = entry.getKey();
                Set<String> oldAttributes = entry.getValue();
                
                // Ces anciens attributs sont maintenant des objets
                for (String oldAttr : oldAttributes) {
                    try {
                        if (getObject(oldAttr) != null) {
                            objectToGroupMap.put(oldAttr, groupName);
                            System.out.println("[TRANSPOSE]   → Objet '" + oldAttr + "' assigné au groupe '" + groupName + "'");
                        }
                    } catch (Exception ex) {
                        // Objet n'existe pas après transpose
                    }
                }
            }
        }
        
        // CAS B : On avait des groupes d'OBJETS avant → ils deviennent des groupes d'ATTRIBUTS
        if (hasObjectGroups) {
            System.out.println("[TRANSPOSE] Conversion: groupes d'objets → groupes d'attributs");
            
            // Inverser savedObjectGroups : Map<String, String> → Map<String, Set<String>>
            // oldObject → groupName  →  groupName → {newAttributeNames}
            Map<String, Set<String>> invertedGroups = new HashMap<>();
            
            for (Map.Entry<String, String> entry : savedObjectGroups.entrySet()) {
                String oldObject = entry.getKey();      // ex: "female"
                String groupName = entry.getValue();    // ex: "gender"
                
                // L'ancien objet est maintenant un attribut
                if (getAttributeAtIndex(-1) == null) { // Vérification basique
                    try {
                        // Chercher si oldObject existe maintenant comme attribut
                        if (getAttributes().contains(oldObject)) {
                        	if (!invertedGroups .containsKey(groupName)) { invertedGroups.put(groupName, new HashSet()); } invertedGroups.get(groupName) .add(oldObject);
                        }
                    } catch (Exception ex) {
                        // Attribut n'existe pas
                    }
                }
            }
            
            // Créer les groupes d'attributs avec les attributs transformés
            for (Map.Entry<String, Set<String>> entry : invertedGroups.entrySet()) {
                String groupName = entry.getKey();
                Set<String> newAttributes_InGroup = entry.getValue();
                
                System.out.println("[TRANSPOSE]   → Création groupe d'attributs '" + groupName + "' = " + newAttributes_InGroup);
                String groupId = attributeGroupManager.createGroup(groupName);
                if (groupId != null) {
                    for (String attr : newAttributes_InGroup) {
                        attributeGroupManager.addAttributeToGroup(groupId, attr);
                    }
                }
            }
        }
        
        System.out.println("[TRANSPOSE] ========== FIN DU TRANSPOSE ==========");
        System.out.println("[TRANSPOSE] État après:");
        System.out.println("  - Groupes d'attributs : " + attributeGroupManager.getGroupCount());
        System.out.println("  - Groupes d'objets : " + objectToGroupMap.size());
        System.out.println();
    }
    
    /**
     * Obtenir l'attribut à un index donné
     * 
     * @param index L'index (0-based)
     * @return L'attribut, ou null si index hors limites
     */
    public String getAttributeAtIndex(int index) {
        try {
            if (index < 0 || index >= getAttributeCount())
                return null;
            
            int count = 0;
            for (String attr : getAttributes()) {
                if (count == index)
                    return attr;
                count++;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
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
        if (oldName == null || newName == null || oldName.isEmpty() || newName.isEmpty()) {
            System.out.println("[F1-CASSE] Rename échoué: oldName=" + oldName + ", newName=" + newName);
            return;
        }
        
        // ✅ FIX CASSE : Force minuscule pour les attributs
        String oldNameLower = oldName.toLowerCase();
        String newNameLower = newName.toLowerCase();
        
        // ✅ FIX UNICITÉ : Vérifier que newName n'existe pas (insensible à la casse)
        if (!oldNameLower.equals(newNameLower) && existsAttributeAlready(newNameLower)) {
            System.out.println("[F1-CASSE] Rename échoué: '" + newNameLower + "' existe déjà");
            return;
        }
        
        System.out.println("[F1-CASSE] Renaming attribute: " + oldNameLower + " → " + newNameLower);
        
        IndexedSet<String> newAttributes = new ListSet<>();
        IndexedSet<FullObject<String, String>> filteredObjects = new ListSet<>();
        for (FullObject<String, String> object : objects) {
            if (objectHasAttribute(object, oldNameLower)) {
                filteredObjects.add(object);
                try {
                    removeAttributeFromObject(oldNameLower, object.getIdentifier());
                } catch (IllegalObjectException e) {
                    e.printStackTrace();
                }
            }
        }
        for (String attribute : getAttributes()) {
            if (attribute.equals(oldNameLower)) {
                newAttributes.add(newNameLower);
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
                addAttributeToObject(newNameLower, object.getIdentifier());
            } catch (IllegalObjectException e) {
                e.printStackTrace();
            }
        }
        
        // ✅ FIX GROUPES : Renommer dans les groupes aussi
        AttributeGroup group = getGroupForAttribute(oldNameLower);
        if (group != null) {
            group.removeAttribute(oldNameLower);
            group.addAttribute(newNameLower);
            System.out.println("[F1-CASSE] Attribut renommé dans le groupe '" + group.getGroupName() + "'");
        }
    }

    public void renameObject(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.isEmpty() || newName.isEmpty()) {
            System.out.println("[F1-CASSE] Rename échoué: oldName=" + oldName + ", newName=" + newName);
            return;
        }
        
        // ✅ FIX CASSE : Force minuscule pour les objets
        String oldNameLower = oldName.toLowerCase();
        String newNameLower = newName.toLowerCase();
        
        // ✅ FIX UNICITÉ : Vérifier que newName n'existe pas
        if (!oldNameLower.equals(newNameLower) && existsObjectAlready(newNameLower)) {
            System.out.println("[F1-CASSE] Rename échoué: '" + newNameLower + "' existe déjà");
            return;
        }
        
        System.out.println("[F1-CASSE] Renaming object: " + oldNameLower + " → " + newNameLower);
        
        IndexedSet<FullObject<String, String>> newObjects = new ListSet<>();
        for (FullObject<String, String> object : objects) {
            if (object.getIdentifier().equals(oldNameLower)) {
                newObjects.add(new FullObject<String, String>(newNameLower, getAttributesForObject(oldNameLower)));
            } else {
                newObjects.add(object);
            }
        }
        objects = newObjects;
        for (SortedSet<String> objects : objectsOfAttribute.values()) {
            if (objects.contains(oldNameLower)) {
                objects.remove(oldNameLower);
                objects.add(newNameLower);
            }
        }
        
        // ✅ FIX GROUPES D'OBJETS : Renommer dans objectToGroupMap aussi
        if (objectToGroupMap.containsKey(oldNameLower)) {
            String groupName = objectToGroupMap.remove(oldNameLower);
            objectToGroupMap.put(newNameLower, groupName);
            System.out.println("[F1-CASSE] Objet renommé dans le groupe '" + groupName + "'");
        }
    }

    public boolean existsAttributeAlready(String name) {
        if (name == null) return false;
        String nameLower = name.toLowerCase();
        for (String attribute : getAttributes()) {
            if (attribute.toLowerCase().equals(nameLower))
                return true;
        }
        return false;
    }

    public boolean existsObjectAlready(String name) {
        if (name == null) return false;
        String nameLower = name.toLowerCase();
        for (FullObject<String, String> object : objects) {
            if (object.getIdentifier().toLowerCase().equals(nameLower))
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
        // ✅ FIX CASSE : Force minuscule pour le nouvel objet
        String objNameLower = object.getIdentifier().toLowerCase();
        FullObject<String, String> objLower = new FullObject<>(objNameLower, object.getDescription().getAttributes());
        
        IndexedSet<FullObject<String, String>> newObjects = new ListSet<>();
        for (int j = 0; j < getObjectCount(); j++) {
            if (j == i)
                newObjects.add(objLower);
            newObjects.add(getObjectAtIndex(j));
        }
        if (i == getObjectCount())
            newObjects.add(objLower);
        objects = newObjects;
    }

    public void addAttributeAt(String attribute, int i) {
        // ✅ FIX CASSE : Force minuscule pour le nouvel attribut
        String attrLower = attribute != null ? attribute.toLowerCase() : "attr";
        
        IndexedSet<String> newAttributes = new ListSet<>();
        for (int j = 0; j < getAttributeCount(); j++) {
            if (j == i)
                newAttributes.add(attrLower);
            newAttributes.add(getAttributeAtIndex(j));
        }
        if (i == getAttributeCount())
            newAttributes.add(attrLower);
        getAttributes().clear();
        for (String attr : newAttributes) {
            getAttributes().add(attr);
        }
        objectsOfAttribute.put(attrLower, new TreeSet<String>());
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
     * Obtenir le nom du groupe pour un objet donné
     * 
     * @param objectName Le nom de l'objet
     * @return Le nom du groupe, ou null si l'objet n'est pas dans un groupe
     */
    public String getGroupNameForObject(String objectName) {
        return objectToGroupMap.get(objectName);
    }
     
    /**
     * Vérifier si des groupes d'objets existent
     */
    public boolean hasObjectGroups() {
        return !objectToGroupMap.isEmpty();
    }
     
    /**
     * Obtenir tous les noms de groupes d'objets (uniques)
     */
    public Set<String> getAllObjectGroupNames() {
        return new HashSet<>(objectToGroupMap.values());
    }
 
    /**
     * Create a new attribute group with the given name and attributes
     * @param groupName Name for the group (e.g., "CONDITIONS MÉTÉO")
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
            System.out.println("[F1-DEBUG] Nombre de groupes : " + attributeGroupManager.getGroupCount());
            
            // Afficher chaque groupe et ses attributs
            for (AttributeGroup group : attributeGroupManager.getAllGroups()) {
                System.out.println("[F1-DEBUG] Groupe '" + group.getGroupName() + 
                                 "' contient : " + group.getAttributeNames());
            }
            
            java.util.List<String> newOrder = new java.util.ArrayList<>();
            java.util.Set<String> processed = new java.util.HashSet<>();
            
            // Parcourir les groupes et ajouter leurs attributs ensemble
            for (AttributeGroup group : attributeGroupManager.getAllGroups()) {
                for (String attr : group.getAttributeNames()) {
                    if (!processed.contains(attr)) {
                        newOrder.add(attr);
                        processed.add(attr);
                        System.out.println("[F1-DEBUG] Ajout à newOrder : " + attr);
                    }
                }
            }
            
            // Ajouter les attributs non groupés à la fin
            for (String attr : getAttributes()) {
                if (!processed.contains(attr)) {
                    newOrder.add(attr);
                    System.out.println("[F1-DEBUG] Attr non groupé : " + attr);
                }
            }
            
            System.out.println("[F1-DEBUG] Nouvel ordre final : " + newOrder);
            
            // Réorganiser les attributs dans le contexte
            reorderAttributes(newOrder);
            
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

    /**
     * (F1) NOUVEAU : Auto-créer des groupes pour les attributs orphelins
     * 
     * Si un attribut n'est pas dans un groupe, on crée automatiquement un groupe
     * avec le nom de l'attribut EN MAJUSCULES
     * 
     * Exemple :
     * - "female" (non-groupé) → crée groupe "FEMALE" contenant "female"
     * - "adult" (non-groupé) → crée groupe "ADULT" contenant "adult"
     */
    public void ensureUngroupedAttributesHaveGroup() {
        System.out.println("[F1] Vérification des attributs orphelins...");
        
        java.util.Collection<String> ungrouped = getUngroupedAttributes();
        
        if (ungrouped != null && !ungrouped.isEmpty()) {
            System.out.println("[F1] Attributs orphelins trouvés : " + ungrouped);
            
            for (String attr : ungrouped) {
                // Créer un groupe avec le nom de l'attribut en MAJUSCULES
                String groupName = attr.toUpperCase();
                
                // Vérifier que le nom de groupe n'existe pas déjà
                if (!attributeGroupManager.groupNameExists(groupName)) {
                    System.out.println("[F1] Création du groupe '" + groupName + "' pour l'attribut '" + attr + "'");
                    String groupId = attributeGroupManager.createGroup(groupName);
                    if (groupId != null) {
                        attributeGroupManager.addAttributeToGroup(groupId, attr);
                    }
                } else {
                    System.out.println("[F1] Groupe '" + groupName + "' existe déjà, ajout de l'attribut '" + attr + "'");
                    // Trouver le groupe et ajouter l'attribut
                    AttributeGroup group = attributeGroupManager.getGroupByName(groupName);
                    if (group != null && !group.containsAttribute(attr)) {
                        attributeGroupManager.addAttributeToGroup(group.getGroupId(), attr);
                    }
                }
            }
        }
        
        System.out.println("[F1] Vérification complète. Groupes totaux : " + attributeGroupManager.getGroupCount());
    }
    
 // ═════════════════════════════════════════════════════════════
    // FIX UNDO/REDO : Setters pour AttributeGroupManager
    // ═════════════════════════════════════════════════════════════

    /**
     * Set the attribute group manager (used by undo/redo)
     * 
     * (FIX UNDO/REDO) : Cette méthode est CRUCIALE pour restaurer 
     * l'état des groupes lors des opérations undo/redo
     * 
     * @param newManager Le nouveau manager à utiliser
     */
    public void setAttributeGroupManager(AttributeGroupManager newManager) {
        if (newManager != null) {
            this.attributeGroupManager = newManager;
        }
    }

    /**
     * (FIX UNDO/REDO) : Vérifier si le manager est bien configuré
     * 
     * @return true si le manager n'est pas null
     */
    public boolean hasAttributeGroupManager() {
        return this.attributeGroupManager != null;
    }

    /**
     * (FIX CASSE) Getter pour objectToGroupMap
     */
    public Map<String, String> getObjectToGroupMap() {
        return new HashMap<>(objectToGroupMap);
    }

    /**
     * (FIX CASSE) Setter pour objectToGroupMap
     */
    public void setObjectToGroupMap(Map<String, String> newMap) {
        this.objectToGroupMap = (Map<String, String>) ((newMap != null) ? new HashMap<>(newMap) : new HashMap<>());
    }
}