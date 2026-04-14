package fcatools.conexpng.gui.lattice;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

import de.tudresden.inf.tcs.fcaapi.Concept;
import de.tudresden.inf.tcs.fcalib.FullObject;
import de.tudresden.inf.tcs.fcalib.utils.ListSet;
import fcatools.conexpng.Conf;
import fcatools.conexpng.gui.lattice.algorithms.ExternalFreeseLatticeGraphAlgorithm;
import fcatools.conexpng.gui.lattice.algorithms.FreeseLatticeGraphAlgorithm;
import fcatools.conexpng.gui.lattice.algorithms.ILatticeGraphAlgorithm;
import fcatools.conexpng.gui.lattice.algorithms.TrivialLatticeGraphAlgorithm;
import fcatools.conexpng.io.locale.LocaleHandler;
import fcatools.conexpng.model.FormalContext;

/**
 * This class computes the lattice graph. It contains the available algorithms
 * for lattice layout and provides methods to change the default.
 */
public class LatticeGraphComputer {

    private static LatticeGraph graph;
    private static Set<Concept<String, FullObject<String, String>>> lattConcepts;
    private static HashMap<String, ILatticeGraphAlgorithm> algorithms;
    private static ILatticeGraphAlgorithm usedAlgorithm;
    private static int screenWidth;
    private static int screenHeight;

    // (F1) NOUVEAU CHAMP pour Fonctionnalité 1 : Groupes d'Attributs
    // Stocke l'ID du groupe actuellement sélectionné pour la génération du treillis
    // Si null → générer le treillis pour TOUS les attributs (comportement normal)
    // Si non-null → générer le treillis SEULEMENT pour les attributs du groupe spécifié
    private static String currentGroupIdForLattice = null;

    // (F1) NOUVEAU CHAMP pour accéder au contexte depuis les méthodes statiques
    // Nécessaire pour accéder à getAttributesInGroup() de FormalContext
    private static Conf currentConf = null;

    /**
     * Initialize: Create algorithms, select default.
     */
    public static void init() {
        algorithms = new HashMap<>();
        algorithms.put("Trivial", new TrivialLatticeGraphAlgorithm());
        algorithms.put("Freese", new FreeseLatticeGraphAlgorithm());
        algorithms.put("ExternalFreese", new ExternalFreeseLatticeGraphAlgorithm());
        usedAlgorithm = algorithms.get("ExternalFreese");
    }

    /**
     * Change the selected algorithm.
     * 
     * @param name
     *            name of algorithm
     */
    public static void chooseAlgorithm(String name) {
        if (!algorithms.containsKey(name)) {
            System.err.println(LocaleHandler.getString("LatticeGraphComputer.chooseAlgorithm.error"));
        } else {
            usedAlgorithm = algorithms.get(name);
        }
    }

    // (F1) NOUVELLES MÉTHODES pour Fonctionnalité 1 : Groupes d'Attributs
    // Ces méthodes permettent de définir quel groupe générer et de le récupérer

    /**
     * (F1) Définir l'ID du groupe pour lequel générer le treillis
     * 
     * Utilisé par GenerateLatticeForGroupAction pour dire au LatticeGraphComputer
     * quel groupe spécifique générer.
     * 
     * @param groupId ID du groupe (ou null pour générer pour tous les attributs)
     */
    public static void setGroupIdForLattice(String groupId) {
        currentGroupIdForLattice = groupId;
    }

    /**
     * (F1) Récupérer l'ID du groupe actuellement défini
     * 
     * @return ID du groupe ou null si génération pour tous les attributs
     */
    public static String getGroupIdForLattice() {
        return currentGroupIdForLattice;
    }

    /**
     * (F1) Définir la configuration (Conf) pour accéder au contexte
     * Nécessaire pour que filterConceptsByGroup() puisse accéder au contexte
     * 
     * @param conf La configuration contenant le FormalContext
     */
    public static void setConf(Conf conf) {
        currentConf = conf;
    }

    /**
     * This method computes the lattice graph.
     * 
     * (F1) MODIFIÉ pour Fonctionnalité 1 : Support des groupes d'attributs
     * Si currentGroupIdForLattice != null, filtre les concepts pour ce groupe uniquement
     * 
     * @param concepts
     *            set of concepts of the lattice.
     * @param bounds
     *            of the viewport
     * @return the lattice graph which has to be drawn
     */
    public static LatticeGraph computeLatticeGraph(Set<Concept<String, FullObject<String, String>>> concepts,
            Rectangle bounds) {
        // (F1) Si un groupe est sélectionné, filtrer les concepts pour ce groupe uniquement
        if (currentGroupIdForLattice != null && currentConf != null) {
            lattConcepts = filterConceptsByGroup(concepts, currentGroupIdForLattice);
        } else {
            lattConcepts = concepts;
        }

        screenWidth = bounds.width;
        screenHeight = bounds.height;
        initGraph();
        graph.computeAllIdeals();
        computeVisibleObjectsAndAttributes();
        graph = usedAlgorithm.computeLatticeGraphPositions(graph, screenWidth, screenHeight);
        return graph;
    }

    /**
     * Initialize the graph.
     */
    public static void initGraph() {
        graph = new LatticeGraph();

        Iterator<Concept<String, FullObject<String, String>>> iter = lattConcepts.iterator();
        while (iter.hasNext()) {
            Node n = new Node();
            Concept<String, FullObject<String, String>> c = (Concept<String, FullObject<String, String>>) iter.next();
            n.addAttributs(c.getIntent());

            ListSet<String> extent = new ListSet<>();
            for (FullObject<String, String> fo : c.getExtent()) {
                extent.add(fo.getIdentifier());
            }
            n.getObjects().addAll(extent);
            graph.getNodes().add(n);
        }

        graph.removeAllDuplicates();

        List<Node> topNode = new ArrayList<>();
        for (Node u : graph.getNodes()) {
            topNode.add(u);
            Set<String> uEx = u.getObjects();
            for (Node v : graph.getNodes()) {
                Set<String> vEx = v.getObjects();
                if (isLowerNeighbour(uEx, vEx)) {
                    v.addChildNode(u);
                    u.addParentNode(v);
                    graph.getEdges().add(new Edge(u, v));
                    topNode.remove(u);
                }
            }
        }
        Queue<Node> q = new LinkedList<>();
        q.addAll(topNode);
        while (!q.isEmpty()) {
            Node n = q.remove();
            for (Node v : n.getChildNodes()) {
                if (v.getLevel() == 0 || v.getLevel() == n.getLevel()) {
                    v.setLevel(n.getLevel() + 1);
                    graph.setMaxLevel(v.getLevel());
                    v.setX((int) (Math.random() * 500));
                    v.setY(100 * v.getLevel());
                    v.positionLabels();
                    q.add(v);
                }
            }
        }

    }

    /**
     * 
     * @param subEx
     * @param superEx
     * @return
     */
    public static boolean isSubconcept(Set<String> subEx, Set<String> superEx) {
        if (subEx == superEx) {
            return false;
        }
        if (subEx.size() > superEx.size()) {
            return false;
        }
        for (String s : subEx) {
            if (!superEx.contains(s)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 
     * @param subEx
     * @param superEx
     * @return
     */
    public static boolean isLowerNeighbour(Set<String> subEx, Set<String> superEx) {
        if (subEx == superEx) {
            return false;
        }
        if (!isSubconcept(subEx, superEx)) {
            return false;
        }
        for (Node n : graph.getNodes()) {
            Set<String> set = n.getObjects();
            if (!subEx.equals(set)) {
                if (!superEx.equals(set)) {
                    if (isSubconcept(subEx, set)) {
                        if (isSubconcept(set, superEx)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    public static void computeVisibleObjectsAndAttributes() {
        // calc which obj/attr has to be shown
        Set<String> usedObj = new TreeSet<>();
        Set<String> usedAttr = new TreeSet<>();
        Node maxNode = new Node();
        Node minNode;
        if (graph.getNodes().size() == 0) {
            minNode = new Node();
        } else {
            minNode = graph.getNode(0);
        }

        for (Node u : graph.getNodes()) {
            if (u.getIdeal().size() >= maxNode.getIdeal().size()) {
                maxNode = u;
            } else if (u.getIdeal().size() <= minNode.getIdeal().size()) {
                minNode = u;
            }
        }

        Queue<Node> pq = new LinkedList<>();
        pq.add(maxNode);
        while (!pq.isEmpty()) {
            Node n = pq.remove();
            for (String a : n.getAttributes()) {
                if (!usedAttr.contains(a)) {
                    n.setVisibleAttribute(a);
                    usedAttr.add(a);
                }
            }
            for (Node u : n.getChildNodes()) {
                pq.add(u);
            }
        }

        pq.add(minNode);
        while (!pq.isEmpty()) {
            Node n = pq.remove();
            for (String o : n.getObjects()) {
                if (!usedObj.contains(o)) {
                    n.setVisibleObject(o);
                    usedObj.add(o);
                }
            }
            for (Node u : graph.getNodes()) {
                if (u.getChildNodes().contains(n)) {
                    pq.add(u);
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // (F1) NOUVELLES MÉTHODES HELPER pour Fonctionnalité 1 : Groupes d'Attributs
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * (F1) Filtrer les concepts pour un groupe spécifique
     * 
     * Cette méthode prend l'ensemble de tous les concepts et ne garde que ceux
     * dont les attributs (intent) appartiennent au groupe spécifié.
     * 
     * Exemple :
     * - Contexte original : 10 attributs → 1024 concepts possibles
     * - Groupe "Conditions Météo" : 3 attributs (Chaleur, Pluie, Humidité)
     * - Résultat : Seulement les concepts contenant SEULEMENT ces 3 attributs
     * - Nombre de concepts réduits : max 2^3 = 8 concepts
     * 
     * Cela rend l'analyse plus simple en se concentrant sur une famille d'attributs liés
     * 
     * @param allConcepts Tous les concepts du contexte original
     * @param groupId ID du groupe pour filtrer
     * @return Ensemble des concepts filtrés pour ce groupe
     */
    private static Set<Concept<String, FullObject<String, String>>> filterConceptsByGroup(
            Set<Concept<String, FullObject<String, String>>> allConcepts,
            String groupId) {

        Set<Concept<String, FullObject<String, String>>> filteredConcepts = new TreeSet<>();

        // (F1) Récupérer les attributs du groupe depuis le contexte actuel
        // ✅ COMPLÉTÉ : Accès au FormalContext via Conf
        Set<String> groupAttributes = getAttributesInGroup(groupId);

        if (groupAttributes.isEmpty()) {
            // Si le groupe n'a pas d'attributs ou n'existe pas, retourner tous les concepts
            return allConcepts;
        }

        // Parcourir tous les concepts et ne garder que ceux dont les attributs
        // font partie du groupe
        for (Concept<String, FullObject<String, String>> concept : allConcepts) {
            Set<String> conceptAttributes = concept.getIntent();

            // Vérifier si TOUS les attributs du concept appartiennent au groupe
            boolean allAttributesInGroup = true;
            for (String attr : conceptAttributes) {
                if (!groupAttributes.contains(attr)) {
                    allAttributesInGroup = false;
                    break;
                }
            }

            // Si tous les attributs du concept font partie du groupe, le garder
            if (allAttributesInGroup) {
                filteredConcepts.add(concept);
            }
        }

        return filteredConcepts;
    }

    /**
     * (F1) Générer le treillis pour un groupe spécifique
     * 
     * Encapsulation pratique pour :
     * 1. Définir le groupe à générer
     * 2. Déclencher le recalcul du treillis
     * 
     * Utilisé par GenerateLatticeForGroupAction
     * 
     * @param groupId ID du groupe pour lequel générer le treillis
     */
    public static void computeLatticeGraphForGroup(String groupId) {
        currentGroupIdForLattice = groupId;
        // Note: L'appel réel à computeLatticeGraph() se fera depuis le contrôleur
        // qui appellera state.latticeChanged() pour déclencher la mise à jour
    }

    /**
     * (F1) Récupérer les attributs d'un groupe
     * 
     * Utilitaire pour obtenir la liste des attributs appartenant à un groupe.
     * Utilisé par filterConceptsByGroup() pour savoir quels attributs faire
     * correspondre lors du filtrage.
     * 
     * ✅ COMPLÉTÉ : Accès au contexte via Conf pour récupérer les attributs
     * 
     * @param groupId ID du groupe
     * @return Set d'attributs appartenant au groupe
     */
    public static Set<String> getAttributesInGroup(String groupId) {
        // (F1) ✅ COMPLÉTÉ : Implémenter en accédant au contexte global
        if (currentConf == null || currentConf.context == null) {
            return new TreeSet<>();  // Retourner un ensemble vide si le contexte n'est pas disponible
        }

        // ✅ Accéder au FormalContext pour récupérer les attributs du groupe
        FormalContext context = currentConf.context;
        
        // Appeler la méthode getAttributesInGroup() qui existe maintenant dans FormalContext
        java.util.List<String> groupAttributesList = context.getAttributesInGroup(groupId);
        
        if (groupAttributesList != null && !groupAttributesList.isEmpty()) {
            return new TreeSet<>(groupAttributesList);
        } else {
            return new TreeSet<>();
        }
    }

    /**
     * (F1) Réinitialiser le groupe sélectionné
     * 
     * Revenir au mode normal (tous les attributs).
     * Utile quand l'utilisateur veut basculer entre "treillis du groupe" et "treillis complet"
     */
    public static void clearGroupFilter() {
        currentGroupIdForLattice = null;
    }
}