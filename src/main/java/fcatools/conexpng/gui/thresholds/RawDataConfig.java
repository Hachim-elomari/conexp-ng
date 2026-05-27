package fcatools.conexpng.gui.thresholds;

/**
 * Stores the raw data configuration for one attribute.
 *
 * Supports 5 modes:
 * - THRESHOLD : user defines threshold only (Min/Max grayed out)
 * - MIN_MAX   : user defines Min/Max interval, values inside -> checked
 * - GEOMETRIC : suite géométrique — n classes, requires min > 0, max > 0
 * - UNIFORME  : découpage uniforme — n classes de même taille sur [min, max]
 * - OTHER     : reserved for future use (Renard series)
 */
public class RawDataConfig {

    public enum Mode {
        THRESHOLD,  // User provides threshold directly
        MIN_MAX,    // User provides Min/Max interval
        GEOMETRIC,  // Suite géométrique : n classes entre min et max
        UNIFORME,   // Découpage uniforme : n classes de même taille
        OTHER       // Reserved: Renard series (not implemented yet)
    }

    private String attributeName;
    private Mode   mode;
    private double min;
    private double max;
    private double threshold;
    private int    numberOfClasses = 3;

    public RawDataConfig(String attributeName) {
        this.attributeName   = attributeName;
        this.mode            = Mode.THRESHOLD;
        this.min             = 0.0;
        this.max             = 0.0;
        this.threshold       = 0.0;
        this.numberOfClasses = 3;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getAttributeName()   { return attributeName; }
    public Mode   getMode()            { return mode; }
    public double getMin()             { return min; }
    public double getMax()             { return max; }
    public double getThreshold()       { return threshold; }
    public int    getNumberOfClasses() { return numberOfClasses; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setAttributeName(String a) { this.attributeName = a; }
    public void setMode(Mode mode)         { this.mode = mode; }
    public void setMin(double min)         { this.min = min; }
    public void setMax(double max)         { this.max = max; }
    public void setThreshold(double t)     { this.threshold = t; }
    public void setNumberOfClasses(int n)  { this.numberOfClasses = Math.max(2, n); }

    // ── Mode-specific field editability ─────────────────────────────────────

    /**
     * Min est éditable UNIQUEMENT en mode MIN_MAX.
     * Pour GEOMETRIC et UNIFORME, la valeur est calculée automatiquement
     * depuis les données de la matrice — l'utilisateur ne peut pas la modifier.
     */
    public boolean isMinEditable() {
        return mode == Mode.MIN_MAX;
    }

    /**
     * Max est éditable UNIQUEMENT en mode MIN_MAX.
     * Pour GEOMETRIC et UNIFORME, la valeur est calculée automatiquement
     * depuis les données de la matrice — l'utilisateur ne peut pas la modifier.
     */
    public boolean isMaxEditable() {
        return mode == Mode.MIN_MAX;
    }

    /**
     * La colonne Threshold est éditable uniquement en mode THRESHOLD.
     * Pour GEOMETRIC et UNIFORME, le spinner de classes est géré par l'éditeur.
     */
    public boolean isThresholdEditable() {
        return mode == Mode.THRESHOLD;
    }

    // ── Conversion logic ────────────────────────────────────────────────────

    /**
     * Indique si une valeur doit produire une croix dans le contexte formel.
     *
     * NOTE : GEOMETRIC et UNIFORME créent plusieurs colonnes booléennes.
     * Cette méthode NE DOIT PAS être appelée pour ces modes — retourne false
     * comme repli sûr. Utiliser GeometricScale / UniformScale à la place.
     */
    public boolean shouldBeChecked(double value) {
        switch (mode) {
            case THRESHOLD: return value >= threshold;
            case MIN_MAX:   return value >= min && value <= max;
            case GEOMETRIC: return false; // géré par applyGeometricAttribute()
            case UNIFORME:  return false; // géré par applyUniformAttribute()
            case OTHER:     return value >= threshold;
            default:        return value >= threshold;
        }
    }

    // ── Validation ──────────────────────────────────────────────────────────

    /**
     * Valide la configuration pour le mode GEOMETRIC.
     * @return message d'erreur, ou null si valide.
     */
    public String validateGeometric() {
        if (min <= 0 || max <= 0)
            return "Min et Max doivent être strictement positifs (>0) pour la suite géométrique.";
        if (min >= max)
            return "Min doit être strictement inférieur à Max.";
        if (numberOfClasses < 2)
            return "Le nombre de classes doit être au moins 2.";
        return null;
    }

    /**
     * Valide la configuration pour le mode UNIFORME.
     * Contrairement à GEOMETRIC, min et max peuvent être négatifs ou nuls.
     * @return message d'erreur, ou null si valide.
     */
    public String validateUniform() {
        if (min >= max)
            return "Min doit être strictement inférieur à Max.";
        if (numberOfClasses < 2)
            return "Le nombre de classes doit être au moins 2.";
        return null;
    }
}