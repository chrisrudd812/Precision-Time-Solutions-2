package timeclock.accruals;

import java.sql.*;

public class Accruals {

	static { // Static block - runs once when the class is loaded --***--This is critical for
		// the database connection to succeed.
		String derbyHome = System.getProperty("catalina.base") + "/TimeClockDB"; // Or any other location
		System.setProperty("derby.system.home", derbyHome);
		try {
			Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Failed to load Derby driver: " + e.getMessage(), e); // Stop initialization
		}
	}

	public static String showAccruals() {

		String dbURL = "jdbc:derby:C:\\Users\\chris\\eclipse-workspace\\Clockify\\TimeclockDB;create=true";
		String name;
		int vacation;
		int sick;
		int personal;
		String html = "";

		try {

			Connection con = DriverManager.getConnection(dbURL);

			PreparedStatement psGetAccruals = con.prepareStatement("SELECT * FROM ACCRUALS");
			ResultSet rs = psGetAccruals.executeQuery();

			while (rs.next()) {

				name = rs.getString(1);
				vacation = rs.getInt(2);
				sick = rs.getInt(3);
				personal = rs.getInt(4);

				html += name + "</td><td>" + vacation + "</td><td>" + sick + "</td><td>" + personal
						+ "</td></tr><tr><td>";

			}

			int length = html.length(); // trims the last "</td></tr><tr><td>" off the end of the final string
			html = html.substring(0, length - 8);

			// Close all resources
			con.close();
			rs.close();
			psGetAccruals.close();

		} catch (SQLException e) {

			e.printStackTrace();
		}
		return html;

	}

}
