-- Quick test queries to verify webhook database updates

-- Check recent webhook activity (adjust table name as needed)
SELECT * FROM webhook_logs 
ORDER BY created_at DESC 
LIMIT 10;

-- Check if your target table was updated recently
SELECT COUNT(*) as recent_updates 
FROM your_table_name 
WHERE updated_at > NOW() - INTERVAL 1 HOUR;

-- Create a simple webhook test log table if needed
CREATE TABLE IF NOT EXISTS webhook_test_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    webhook_data JSON,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);