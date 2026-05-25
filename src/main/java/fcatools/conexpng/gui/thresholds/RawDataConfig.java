package fcatools.conexpng.gui.thresholds;

/**
 * Stores the raw data configuration for one attribute.
 *
 * Supports 3 modes:
 * - THRESHOLD: user defines threshold only (Min/Max grayed out)
 * - MIN_MAX: user defines Min/Max interval, values inside interval → checked
 * - OTHER: reserved for future use (Renard series) - all fields grayed for now
 */
public class RawDataConfig {

    /**
     * Mode determines which fields are editable and how to evaluate values.
     */
    public enum Mode {
        THRESHOLD,  // User provides threshold directly, Min/Max disabled
        MIN_MAX,    // User provides Min/Max, value in [Min, Max] → checked
        OTHER       // Reserved for Renard series (not implemented yet)
    }

    private String attributeName;
    private Mode   mode;
    private double min;
    private double max;
    private double threshold;

    /**
     * Default config: THRESHOLD mode (manual), all values at 0.
     */
    public RawDataConfig(String attributeName) {
        this.attributeName = attributeName;
        this.mode       = Mode.THRESHOLD;  // Default: user provides threshold
        this.min        = 0.0;
        this.max        = 0.0;
        this.threshold  = 0.0;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getAttributeName() { return attributeName; }
    public Mode   getMode()          { return mode; }
    public double getMin()           { return min; }
    public double getMax()           { return max; }
    public double getThreshold()     { return threshold; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setAttributeName(String attributeName) { this.attributeName = attributeName; }
    public void setMode(Mode mode)                     { this.mode = mode; }
    public void setMin(double min)                     { this.min = min; }
    public void setMax(double max)                     { this.max = max; }
    public void setThreshold(double threshold)         { this.threshold = threshold; }

    // ── Mode-specific logic ──────────────────────────────────────────────────

    /**
     * Determine which fields are editable based on the current mode.
     */
    public boolean isMinEditable() {
        return mode == Mode.MIN_MAX;  // Only editable in Min/Max mode
    }

    public boolean isMaxEditable() {
        return mode == Mode.MIN_MAX;  // Only editable in Min/Max mode
    }

    public boolean isThresholdEditable() {
        return mode == Mode.THRESHOLD;  // Only editable in Threshold mode
    }

    /**
     * Determine if a value should result in a checked box, based on the current mode.
     * 
     * THRESHOLD mode: value >= threshold → checked (GREEN)
     * MIN_MAX mode: min <= value <= max → checked (GREEN)
     * OTHER mode: placeholder (uses threshold for now)
     */
    public boolean shouldBeChecked(double value) {
        switch (mode) {
            case THRESHOLD:
                return value >= threshold;  // >= to include exact match

            case MIN_MAX:
                return value >= min && value <= max;  // Inside interval [min, max]

            case OTHER:
                return value >= threshold;  // Placeholder (to be implemented)

            default:
                return value >= threshold;
        }
    }
}