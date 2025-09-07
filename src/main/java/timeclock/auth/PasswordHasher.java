package timeclock.auth;

import org.mindrot.jbcrypt.BCrypt;

/**
 * A simple, one-time-use class to generate a BCrypt hash
 * using the project's specific jbcrypt library version.
 */
public class PasswordHasher {

    public static void main(String[] args) {
        // The password you want to hash
        String passwordToHash = "Pyramid1!";

        // Generate a salt and hash the password. 
        // The '12' is the "work factor" or strength.
        String hashedPassword = BCrypt.hashpw(passwordToHash, BCrypt.gensalt(12));

        // Print the new, compatible hash to the console
        System.out.println("==============================================================");
        System.out.println("Your new BCrypt hash is:");
        System.out.println(hashedPassword);
        System.out.println("==============================================================");
        System.out.println("Copy the line above that starts with $2a$12...");
    }
}