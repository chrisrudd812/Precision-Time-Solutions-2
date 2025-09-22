package timeclock.settings;

import java.util.HashMap;
import java.util.Map;

/**
 * State-specific overtime rules for Pro plan users.
 * This class defines overtime calculation rules based on employee's state.
 */
public class StateOvertimeRules {
    
    private static final Map<String, StateOvertimeRuleDetail> STATE_RULES = new HashMap<>();
    
    static {
        // California - Daily OT after 8 hours, Double time after 12 hours
        STATE_RULES.put("CA", new StateOvertimeRuleDetail(
            true, 8.0,      // Daily OT enabled, threshold 8 hours
            true, 12.0,     // Double time enabled, threshold 12 hours  
            "1.5",          // Standard OT rate
            true, 8.0, 12.0 // 7th day: first 8 hours at 1.5x, after 8 hours at 2.0x
        ));
        
        // Alaska - Daily OT after 8 hours
        STATE_RULES.put("AK", new StateOvertimeRuleDetail(
            true, 8.0,      // Daily OT enabled, threshold 8 hours
            false, 0.0,     // No double time
            "1.5",          // Standard OT rate
            false, 0.0, 0.0 // No 7th day rules
        ));
        
        // Nevada - Daily OT after 8 hours
        STATE_RULES.put("NV", new StateOvertimeRuleDetail(
            true, 8.0,      // Daily OT enabled, threshold 8 hours
            false, 0.0,     // No double time
            "1.5",          // Standard OT rate
            false, 0.0, 0.0 // No 7th day rules
        ));
        
        // Colorado - Daily OT after 12 hours
        STATE_RULES.put("CO", new StateOvertimeRuleDetail(
            true, 12.0,     // Daily OT enabled, threshold 12 hours
            false, 0.0,     // No double time
            "1.5",          // Standard OT rate
            false, 0.0, 0.0 // No 7th day rules
        ));
        
        // District of Columbia - Standard FLSA rules (weekly OT only)
        // Note: DC follows federal FLSA rules, no daily OT
        // We don't add DC to STATE_RULES so it uses standard weekly OT calculation
        
        // Default FLSA rules for all other states (weekly OT only)
        // Will be applied when no state-specific rule exists
    }
    
    /**
     * Get overtime rules for a specific state.
     * Returns null if state follows standard FLSA rules (weekly OT only).
     */
    public static StateOvertimeRuleDetail getRulesForState(String stateCode) {
        if (stateCode == null || stateCode.trim().isEmpty()) {
            return null; // Use FLSA default
        }
        return STATE_RULES.get(stateCode.toUpperCase().trim());
    }
    
    /**
     * Check if a state has special overtime rules.
     */
    public static boolean hasSpecialRules(String stateCode) {
        return getRulesForState(stateCode) != null;
    }
    
    /**
     * Get all supported states with special overtime rules.
     */
    public static String[] getStatesWithSpecialRules() {
        return STATE_RULES.keySet().toArray(new String[0]);
    }
}