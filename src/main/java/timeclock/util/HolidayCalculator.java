package timeclock.util;

import java.time.LocalDate;
import java.time.Month;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import java.util.List;
import timeclock.Configuration;

/**
 * Utility class for calculating US Federal Holidays and checking if a date is a configured holiday.
 */
public class HolidayCalculator {

    /**
     * Calculates all US Federal Holidays for a given year, including observed dates.
     * @param year The year to calculate holidays for
     * @return Set of LocalDate objects representing federal holidays (observed dates)
     */
    public static Set<LocalDate> calculateUSFederalHolidays(int year) {
        Set<LocalDate> holidays = new HashSet<>();
        
        // New Year's Day - January 1 (observed)
        holidays.add(getObservedDate(LocalDate.of(year, Month.JANUARY, 1)));
        
        // Martin Luther King Jr. Day - Third Monday in January
        holidays.add(LocalDate.of(year, Month.JANUARY, 1)
            .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))
            .plusWeeks(2));
        
        // Presidents' Day - Third Monday in February
        holidays.add(LocalDate.of(year, Month.FEBRUARY, 1)
            .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))
            .plusWeeks(2));
        
        // Memorial Day - Last Monday in May
        holidays.add(LocalDate.of(year, Month.MAY, 1)
            .with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)));
        
        // Juneteenth - June 19 (observed)
        holidays.add(getObservedDate(LocalDate.of(year, Month.JUNE, 19)));
        
        // Independence Day - July 4 (observed)
        holidays.add(getObservedDate(LocalDate.of(year, Month.JULY, 4)));
        
        // Labor Day - First Monday in September
        holidays.add(LocalDate.of(year, Month.SEPTEMBER, 1)
            .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY)));
        
        // Columbus Day - Second Monday in October
        holidays.add(LocalDate.of(year, Month.OCTOBER, 1)
            .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))
            .plusWeeks(1));
        
        // Veterans Day - November 11 (observed)
        holidays.add(getObservedDate(LocalDate.of(year, Month.NOVEMBER, 11)));
        
        // Thanksgiving - Fourth Thursday in November
        holidays.add(LocalDate.of(year, Month.NOVEMBER, 1)
            .with(TemporalAdjusters.firstInMonth(DayOfWeek.THURSDAY))
            .plusWeeks(3));
        
        // Christmas - December 25 (observed)
        holidays.add(getObservedDate(LocalDate.of(year, Month.DECEMBER, 25)));
        
        return holidays;
    }
    
    /**
     * Gets the observed date for a holiday that falls on a weekend.
     * Federal rule: If Saturday, observed on Friday. If Sunday, observed on Monday.
     * @param actualDate The actual holiday date
     * @return The observed date
     */
    private static LocalDate getObservedDate(LocalDate actualDate) {
        DayOfWeek dayOfWeek = actualDate.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY) {
            return actualDate.minusDays(1); // Friday
        } else if (dayOfWeek == DayOfWeek.SUNDAY) {
            return actualDate.plusDays(1); // Monday
        }
        return actualDate; // Weekday, no change
    }
    
    /**
     * Checks if a given date is a configured holiday for the tenant.
     * @param date The date to check
     * @param tenantId The tenant ID
     * @return true if the date is a configured holiday, false otherwise
     */
    public static boolean isConfiguredHoliday(LocalDate date, int tenantId) {
        // Check if holiday overtime is enabled
        boolean holidayOTEnabled = "true".equalsIgnoreCase(
            Configuration.getProperty(tenantId, "OvertimeHolidayEnabled", "false"));
        
        if (!holidayOTEnabled) {
            return false;
        }
        
        // Get selected holidays from configuration
        String selectedHolidays = Configuration.getProperty(tenantId, "OvertimeHolidays", "");
        if (selectedHolidays.trim().isEmpty()) {
            return false;
        }
        
        List<String> enabledHolidayIds = Arrays.asList(selectedHolidays.split(","));
        
        // Check custom holiday first
        String customHolidayDate = Configuration.getProperty(tenantId, "CustomHolidayDate", "");
        if (!customHolidayDate.trim().isEmpty()) {
            try {
                LocalDate customDate = LocalDate.parse(customHolidayDate);
                if (date.equals(customDate)) {
                    return true;
                }
            } catch (Exception e) {
                // Invalid custom holiday date, ignore
            }
        }
        
        // Calculate federal holidays for the year
        Set<LocalDate> federalHolidays = calculateUSFederalHolidays(date.getYear());
        
        // Map holiday IDs to dates and check if the date matches any enabled holiday
        for (LocalDate holiday : federalHolidays) {
            String holidayId = getHolidayId(holiday);
            if (holidayId != null && enabledHolidayIds.contains(holidayId)) {
                if (date.equals(holiday)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Maps a holiday date to its corresponding ID used in the configuration.
     * @param holidayDate The holiday date (observed)
     * @return The holiday ID string, or null if not a recognized federal holiday
     */
    private static String getHolidayId(LocalDate holidayDate) {
        int year = holidayDate.getYear();
        
        // Check against observed dates
        if (holidayDate.equals(getObservedDate(LocalDate.of(year, Month.JANUARY, 1)))) {
            return "new_years";
        } else if (holidayDate.equals(LocalDate.of(year, Month.JANUARY, 1)
            .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY)).plusWeeks(2))) {
            return "mlk_day";
        } else if (holidayDate.equals(LocalDate.of(year, Month.FEBRUARY, 1)
            .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY)).plusWeeks(2))) {
            return "presidents_day";
        } else if (holidayDate.equals(LocalDate.of(year, Month.MAY, 1)
            .with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)))) {
            return "memorial_day";
        } else if (holidayDate.equals(getObservedDate(LocalDate.of(year, Month.JUNE, 19)))) {
            return "juneteenth";
        } else if (holidayDate.equals(getObservedDate(LocalDate.of(year, Month.JULY, 4)))) {
            return "independence_day";
        } else if (holidayDate.equals(LocalDate.of(year, Month.SEPTEMBER, 1)
            .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY)))) {
            return "labor_day";
        } else if (holidayDate.equals(LocalDate.of(year, Month.OCTOBER, 1)
            .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY)).plusWeeks(1))) {
            return "columbus_day";
        } else if (holidayDate.equals(getObservedDate(LocalDate.of(year, Month.NOVEMBER, 11)))) {
            return "veterans_day";
        } else if (holidayDate.equals(LocalDate.of(year, Month.NOVEMBER, 1)
            .with(TemporalAdjusters.firstInMonth(DayOfWeek.THURSDAY)).plusWeeks(3))) {
            return "thanksgiving";
        } else if (holidayDate.equals(getObservedDate(LocalDate.of(year, Month.DECEMBER, 25)))) {
            return "christmas";
        }
        
        return null;
    }
}