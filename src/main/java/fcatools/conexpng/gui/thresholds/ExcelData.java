package fcatools.conexpng.gui.thresholds;

import java.util.List;
import java.util.Map;

/**
 * Data structure holding the result of an Excel import.
 * 
 * Contains:
 * - List of attributes (column headers)
 * - List of objects (row headers)
 * - Map of continuous values: object → attribute → value
 * - Statistics: valid vs invalid cells
 */
public class ExcelData {
    
    /** Attribute names from Excel header (e.g., ["woman", "man", "girl"]) */
    public List<String> attributes;
    
    /** Object names from Excel first column (e.g., ["female", "male", "adult"]) */
    public List<String> objects;
    
    /** 
     * Continuous values: object → (attribute → value).
     * Example: "female" → { "woman": 15.5, "man": 22.0 }
     */
    public Map<String, Map<String, Double>> values;
    
    /** Number of cells successfully parsed as numbers */
    public int validCells;
    
    /** Number of cells that were empty or contained text/errors */
    public int invalidCells;
    
    public ExcelData() {
        this.validCells = 0;
        this.invalidCells = 0;
    }
}