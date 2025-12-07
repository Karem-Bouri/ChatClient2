package data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DBManager {

    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/ChatDB";
    private static final String USER = "root";
    private static final String PASSWORD = ""; // Ajustez si nécessaire

    public static Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
        } catch (SQLException e) {
            System.err.println("Erreur de connexion à la base de données: " + e.getMessage());
            throw e;
        }
    }

    // ----------------------------------------------------------------------
    // LOGIQUE UTILISATEUR & AUTHENTIFICATION
    // ----------------------------------------------------------------------

    public static boolean doesUserExist(String pseudo) throws SQLException {
        return getUserIdByPseudo(pseudo) != -1;
    }

    public static boolean checkUserCredentials(String pseudo, String password) throws SQLException {
        String sql = "SELECT mot_de_passe FROM Utilisateurs WHERE pseudo = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, pseudo);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedPassword = rs.getString("mot_de_passe");
                    return storedPassword.equals(password);
                }
            }
        }
        return false;
    }

    public static boolean registerNewUser(String pseudo, String password) throws SQLException {
        String sql = "INSERT INTO Utilisateurs (pseudo, mot_de_passe) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, pseudo);
            stmt.setString(2, password);

            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            // Lève l'exception (ex: pseudo déjà pris)
            throw e;
        }
    }


    public static int getUserIdByPseudo(String pseudo) throws SQLException {
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

    // ----------------------------------------------------------------------
    // LOGIQUE MESSAGERIE & HISTORIQUE (NOUVEAU)
    // ----------------------------------------------------------------------

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
            throw new SQLException("Archivage échoué: ID Utilisateur ou ID Salon introuvable. UserID: " + utilisateurId + ", SalonID: " + salonId);
        }

        // Assurez-vous que la table Messages a une colonne 'horodatage' de type TIMESTAMP
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
        String sql = "SELECT u.pseudo, m.contenu " +
                "FROM Messages m " +
                "JOIN Utilisateurs u ON m.utilisateur_id = u.utilisateur_id " +
                "JOIN Salons s ON m.salon_id = s.salon_id " +
                "WHERE s.nom_salon = ? " +
                "ORDER BY m.horodatage ASC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nomSalon);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String pseudo = rs.getString("pseudo");
                    String contenu = rs.getString("contenu");

                    history.append(pseudo).append(": ").append(contenu).append("\n");
                }
            }
        }

        if (history.length() == 0) {
            return "--- Aucune conversation précédente trouvée. ---\n";
        }
        return history.toString();
    }
}