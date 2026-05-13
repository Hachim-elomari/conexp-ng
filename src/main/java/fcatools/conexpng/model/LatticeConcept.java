package fcatools.conexpng.model;

import java.util.Set;
import de.tudresden.inf.tcs.fcaapi.Concept;
import de.tudresden.inf.tcs.fcalib.FullObject;
import de.tudresden.inf.tcs.fcalib.utils.ListSet;
import fcatools.conexpng.io.locale.LocaleHandler;

/**
 * This class implemented the Concept interface of the fcalib.
 * 
 */
public class LatticeConcept implements Concept<String, FullObject<String, String>> {
    private ListSet<FullObject<String, String>> extent;
    private ListSet<String> intent;
    
    public LatticeConcept() {
        extent = new ListSet<>();
        intent = new ListSet<>();
    }
    
    @Override
    public Set<FullObject<String, String>> getExtent() {
        return this.extent;
    }
    
    @Override
    public Set<String> getIntent() {
        return this.intent;
    }
    
    /**
     * ✅ FIX BUG 4 : Retirer les préfixes de groupe des étiquettes
     * 
     * Les attributs peuvent contenir des préfixes de groupe (ex: "GENDER:male")
     * Cette méthode les nettoie pour n'afficher que le nom de l'attribut
     */
    @Override
    public String toString() {
        // Nettoyer les noms d'attributs (retirer préfixes de groupe)
        ListSet<String> cleanedIntent = new ListSet<>();
        for (String attr : intent) {
            // Si format "GROUPE:attribut", extraire juste "attribut"
            if (attr.contains(":")) {
                String cleaned = attr.substring(attr.indexOf(":") + 1);
                cleanedIntent.add(cleaned);
            } else {
                cleanedIntent.add(attr);
            }
        }
        
        // Nettoyer les noms d'objets (retirer préfixes de groupe)
        ListSet<FullObject<String, String>> cleanedExtent = new ListSet<>();
        for (FullObject<String, String> obj : extent) {
            String objName = obj.getIdentifier();
            // Si format "GROUPE:objet", extraire juste "objet"
            if (objName.contains(":")) {
                objName = objName.substring(objName.indexOf(":") + 1);
            }
            cleanedExtent.add(new FullObject<String, String>(objName, obj.getDescription().getAttributes()));
        }
        
        return LocaleHandler.getString("LatticeConcept.toString.objects") + cleanedExtent + "\n"
                + LocaleHandler.getString("LatticeConcept.toString.attributes") + cleanedIntent + "\n";
    }
}