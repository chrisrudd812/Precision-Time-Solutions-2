# State-Based Overtime Implementation for Pro Plan Users

## Overview
This implementation adds state-based overtime calculations for Pro plan users (100 max users). Employees in different states will have overtime calculated according to their state's specific labor laws rather than just federal FLSA rules.

## Key Features
- **Pro Plan Exclusive**: Only tenants with Pro plan (100 max users) get state-based overtime
- **State-Specific Rules**: Different overtime thresholds and calculations based on employee's state
- **Automatic Fallback**: Non-Pro plans and states without special rules use standard FLSA overtime
- **Comprehensive Coverage**: Handles daily overtime, double time, and 7th day overtime rules

## Supported States with Special Overtime Rules

### California (CA)
- Daily overtime after 8 hours (1.5x rate)
- Double time after 12 hours (2.0x rate)
- 7th consecutive day: First 8 hours at 1.5x, after 8 hours at 2.0x
- Weekly overtime after 40 hours (1.5x rate)

### Alaska (AK)
- Daily overtime after 8 hours (1.5x rate)
- Weekly overtime after 40 hours (1.5x rate)

### Nevada (NV)
- Daily overtime after 8 hours (1.5x rate)
- Weekly overtime after 40 hours (1.5x rate)

### Colorado (CO)
- Daily overtime after 12 hours (1.5x rate)
- Weekly overtime after 40 hours (1.5x rate)

### All Other States
- Standard FLSA rules: Weekly overtime after 40 hours (1.5x rate)

## Implementation Details

### New Classes Created
1. **StateOvertimeRules.java** - Defines state-specific overtime rules
2. **SubscriptionUtils.java** - Utilities for checking Pro plan status
3. **StateOvertimeTest.java** - Test class to verify functionality

### Modified Classes
1. **ShowPayroll.java** - Updated payroll calculations to use state rules
2. **PayrollServlet.java** - Updated punch overtime calculations to use state rules

### Database Requirements
- Uses existing `employee_data.STATE` field for employee's state
- Uses existing `tenants.MaxUsers` field to identify Pro plan (100 users)
- No new database tables required

### How It Works
1. **Plan Check**: System checks if tenant has Pro plan (MaxUsers = 100)
2. **State Detection**: For Pro plan users, system gets employee's state from `employee_data.STATE`
3. **Rule Application**: If state has special rules, those are used; otherwise FLSA rules apply
4. **Calculation**: Overtime is calculated using the appropriate rules for each employee
5. **Logging**: System logs when state-based rules are applied for debugging

### Backward Compatibility
- Non-Pro plan users continue to use existing overtime calculations
- Employees without state information use tenant's default overtime settings
- States without special rules automatically use FLSA standards

### Testing
Run the test class to verify functionality:
```bash
cd src/main/java
javac timeclock/settings/StateOvertimeTest.java
java timeclock.settings.StateOvertimeTest
```

## Benefits for Pro Plan Users
- **Compliance**: Automatic compliance with state-specific labor laws
- **Multi-State Operations**: Different employees can have different overtime rules based on their location
- **Accurate Payroll**: Proper overtime calculations reduce compliance risks and ensure fair pay
- **Scalability**: Easy to add new states with special overtime rules

## Future Enhancements
- Add more states with special overtime rules as needed
- Create admin interface to view/modify state rules
- Add reporting to show which employees use state-based vs. FLSA rules
- Implement state-specific holiday and break time rules