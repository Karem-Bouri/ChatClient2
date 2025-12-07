package data;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.concurrent.atomic.AtomicInteger;

public class DBManager {

    private static final String JDBC_URL = "jdbc:mysql://127.0.0.1:3306/ChatDB";
    private static final String USER = "root";
    private static final String PASSWORD = "";

    public static Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
        } catch (SQLException e) {
            System.err.println("Erreur de connexion à la base de données: " + e.getMessage());
            throw e;
        }
    }

    // --- Méthodes d'authentification et d'inscription ---

    public static boolean checkUserCredentials(String pseudo, String password) throws SQLException {
        String sql = "SELECT mot_de_passe FROM Utilisateurs WHERE pseudo = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, pseudo);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("mot_de_passe").equals(password);
                }
            }
        }
        return false;
    }

    public static boolean registerNewUser(String pseudo, String password) throws SQLException {
        if (doesUserExist(pseudo)) {
            return false;
        }

        String sql = "INSERT INTO Utilisateurs (pseudo, mot_de_passe) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, pseudo);
            stmt.setString(2, password);

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        }
    }

    // Méthode utilitaire
    private static boolean doesUserExist(String pseudo) throws SQLException {
        String sql = "SELECT pseudo FROM Utilisateurs WHERE pseudo = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, pseudo);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int getUserIdByPseudo(String pseudo) throws SQLException {
        String sql = "SELECT utilisateur_id FROM Utilisateurs WHERE pseudo = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pseudo);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("utilisateur_id");
                }
            }
        }
        return -1;
    }

    // --- LOGIQUE MESSAGERIE & HISTORIQUE ---

    public static void saveMessage(String nomSalon, String pseudo, String contenu) throws SQLException {

        int utilisateurId = getUserIdByPseudo(pseudo);

        int salonId = -1;
        String sqlFindSalon = "SELECT salon_id FROM Salons WHERE nom_salon = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmtFindSalon = conn.prepareStatement(sqlFindSalon)) {
            stmtFindSalon.setString(1, nomSalon);
            try (ResultSet rs = stmtFindSalon.executeQuery()) {
                if (rs.next()) {
                    salonId = rs.getInt("salon_id");
                }
            }
        }

        if (utilisateurId == -1 || salonId == -1) {
            throw new SQLException("Archivage échoué: ID Utilisateur ou ID Salon introuvable.");
        }

        String sqlInsert = "INSERT INTO Messages (salon_id, utilisateur_id, contenu) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmtInsert = conn.prepareStatement(sqlInsert)) {

            stmtInsert.setInt(1, salonId);
            stmtInsert.setInt(2, utilisateurId);
            stmtInsert.setString(3, contenu);

            stmtInsert.executeUpdate();
        }
    }

    public static String getSalonHistory(String nomSalon) throws SQLException {
        StringBuilder history = new StringBuilder();

        String sql = "SELECT u.pseudo, m.contenu, m.horodatage " +
                "FROM Messages m " +
                "JOIN Utilisateurs u ON m.utilisateur_id = u.utilisateur_id " +
                "JOIN Salons s ON m.salon_id = s.salon_id " +
                "WHERE s.nom_salon = ? " +
                "ORDER BY m.horodatage ASC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nomSalon);

            try (ResultSet rs = stmt.executeQuery()) {

                SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM HH:mm:ss");

                while (rs.next()) {
                    String pseudo = rs.getString("pseudo");
                    String contenu = rs.getString("contenu");
                    Timestamp timestamp = rs.getTimestamp("horodatage");

                    String formattedTime = dateFormat.format(timestamp);

                    history.append("[").append(formattedTime).append("] ")
                            .append(pseudo).append(": ").append(contenu).append("\n");
                }
            }
        }

        if (history.length() == 0) {
            return "--- Aucune conversation précédente trouvée. ---\n";
        }
        return history.toString();
    }
}