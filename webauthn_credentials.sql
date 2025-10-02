-- WebAuthn credentials table for storing fingerprint authentication data
CREATE TABLE `webauthn_credentials` (
  `CredentialID` int NOT NULL AUTO_INCREMENT,
  `TenantID` int NOT NULL,
  `EID` int NOT NULL,
  `CredentialIdBase64` text COLLATE utf8mb4_general_ci NOT NULL,
  `PublicKeyBase64` text COLLATE utf8mb4_general_ci NOT NULL,
  `DeviceName` varchar(100) COLLATE utf8mb4_general_ci DEFAULT 'Mobile Device',
  `RegisteredAt` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `LastUsedAt` timestamp NULL DEFAULT NULL,
  `IsEnabled` tinyint DEFAULT '1',
  PRIMARY KEY (`CredentialID`),
  KEY `idx_webauthn_tenant_eid` (`TenantID`,`EID`),
  CONSTRAINT `fk_webauthn_employee` FOREIGN KEY (`EID`) REFERENCES `employee_data` (`EID`) ON DELETE CASCADE,
  CONSTRAINT `fk_webauthn_tenant` FOREIGN KEY (`TenantID`) REFERENCES `tenants` (`TenantID`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Add FingerprintEnabled column to employee_data
ALTER TABLE employee_data ADD COLUMN FingerprintEnabled BOOLEAN DEFAULT FALSE;