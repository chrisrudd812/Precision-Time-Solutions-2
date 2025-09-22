package timeclock.settings;

/**
 * Simple test class to verify state overtime rules functionality.
 * This can be run manually to test the state-based overtime implementation.
 */
public class StateOvertimeTest {
    
    public static void main(String[] args) {
        System.out.println("=== State Overtime Rules Test ===");
        
        // Test California rules
        StateOvertimeRuleDetail caRules = StateOvertimeRules.getRulesForState("CA");
        if (caRules != null) {
            System.out.println("California Rules:");
            System.out.println("  Daily OT Enabled: " + caRules.isDailyOTEnabled());
            System.out.println("  Daily OT Threshold: " + caRules.getDailyOTThreshold());
            System.out.println("  Double Time Enabled: " + caRules.isDoubleTimeEnabled());
            System.out.println("  Double Time Threshold: " + caRules.getDoubleTimeThreshold());
            System.out.println("  7th Day OT Enabled: " + caRules.isSeventhDayOTEnabled());
        } else {
            System.out.println("ERROR: California rules not found!");
        }
        
        // Test Alaska rules
        StateOvertimeRuleDetail akRules = StateOvertimeRules.getRulesForState("AK");
        if (akRules != null) {
            System.out.println("\nAlaska Rules:");
            System.out.println("  Daily OT Enabled: " + akRules.isDailyOTEnabled());
            System.out.println("  Daily OT Threshold: " + akRules.getDailyOTThreshold());
            System.out.println("  Double Time Enabled: " + akRules.isDoubleTimeEnabled());
        } else {
            System.out.println("ERROR: Alaska rules not found!");
        }
        
        // Test state with no special rules (should return null)
        StateOvertimeRuleDetail txRules = StateOvertimeRules.getRulesForState("TX");
        if (txRules == null) {
            System.out.println("\nTexas: Uses standard FLSA rules (no special state rules)");
        } else {
            System.out.println("ERROR: Texas should not have special rules!");
        }
        
        // Test states with special rules
        String[] specialStates = StateOvertimeRules.getStatesWithSpecialRules();
        System.out.println("\nStates with special overtime rules:");
        for (String state : specialStates) {
            System.out.println("  " + state);
        }
        
        System.out.println("\n=== Test Complete ===");
    }
}