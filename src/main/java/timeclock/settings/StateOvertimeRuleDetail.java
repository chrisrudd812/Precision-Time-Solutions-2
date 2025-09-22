package timeclock.settings; // Or your relevant package

public class StateOvertimeRuleDetail {
    private boolean dailyOTEnabled;
    private double dailyOTThreshold;
    private boolean doubleTimeEnabled;
    private double doubleTimeThreshold;
    private String standardOTRate; // e.g., "1.5", "2.0"
    // weeklyOTThreshold is fixed at 40 and not part of this POJO as UI doesn't change it
    private boolean seventhDayOTEnabled;
    private double seventhDayOTThreshold; // e.g., first X hours at 1.5x
    private double seventhDayDTThreshold; // e.g., hours after Y at 2.0x
    // Notes are for JS display only, not stored or used by servlet logic directly

    public StateOvertimeRuleDetail(boolean dailyOTEnabled, double dailyOTThreshold, 
                                   boolean doubleTimeEnabled, double doubleTimeThreshold, 
                                   String standardOTRate, boolean seventhDayOTEnabled, 
                                   double seventhDayOTThreshold, double seventhDayDTThreshold) {
        this.dailyOTEnabled = dailyOTEnabled;
        this.dailyOTThreshold = dailyOTThreshold;
        this.doubleTimeEnabled = doubleTimeEnabled;
        this.doubleTimeThreshold = doubleTimeThreshold;
        this.standardOTRate = standardOTRate;
        this.seventhDayOTEnabled = seventhDayOTEnabled;
        this.seventhDayOTThreshold = seventhDayOTThreshold;
        this.seventhDayDTThreshold = seventhDayDTThreshold;
    }

    // Getters
    public boolean isDailyOTEnabled() { return dailyOTEnabled; }
    public double getDailyOTThreshold() { return dailyOTThreshold; }
    public boolean isDoubleTimeEnabled() { return doubleTimeEnabled; }
    public double getDoubleTimeThreshold() { return doubleTimeThreshold; }
    public String getStandardOTRate() { return standardOTRate; }
    public boolean isSeventhDayOTEnabled() { return seventhDayOTEnabled; }
    public double getSeventhDayOTThreshold() { return seventhDayOTThreshold; }
    public double getSeventhDayDTThreshold() { return seventhDayDTThreshold; }
}