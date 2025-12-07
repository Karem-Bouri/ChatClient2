package client;

import ChatSystem.ChatControl;
import ChatSystem.ChatControlHelper;
import data.DBManager;
import java.sql.SQLException;

import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class MainClient {

    private String PSEUDO;
    private ChatControl chatControlRef;

    private Socket chatSocket;
    private PrintWriter writer;

    public static void main(String[] args) {
        new MainClient().startClient(new String[] {});
    }

    public void startClient(String[] args) {

        if (!userLoginRegistration()) {
            System.out.println("Échec de la connexion. Arrêt du client.");
            return;
        }

        try {
            // PHASE 1: Connexion CORBA
            ORB orb = ORB.init(args, null);
            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

            chatControlRef = ChatControlHelper.narrow(ncRef.resolve_str("ChatControl"));

            System.out.println("--- Client CORBA connecté au ChatControl ---");

            clientLoop();

        } catch (Exception e) {
            System.err.println("Erreur de connexion CORBA ou du Service de Nommage: assurez-vous que tnameserv et MainServer sont lancés.");
            e.printStackTrace();
        }
    }

    private boolean userLoginRegistration() {
        Scanner scanner = new Scanner(System.in);
        String pseudo;
        String password;

        System.out.println("--- DÉMARRAGE SÉCURISÉ ---");

        while (true) {
            System.out.print("Entrez votre pseudo: ");
            pseudo = scanner.nextLine();

            try {
                if (DBManager.doesUserExist(pseudo)) {
                    // Utilisateur existant (Ancien utilisateur)
                    System.out.print("Mot de passe: ");
                    password = scanner.nextLine();

                    if (DBManager.checkUserCredentials(pseudo, password)) {
                        System.out.println("Connexion réussie!");
                        this.PSEUDO = pseudo;
                        return true;
                    } else {
                        System.err.println("Mot de passe incorrect.");
                    }

                } else {
                    // Nouvel utilisateur
                    System.out.println("Pseudo non trouvé. Créons un nouveau compte.");
                    System.out.print("Créer un mot de passe: ");
                    password = scanner.nextLine();

                    if (DBManager.registerNewUser(pseudo, password)) {
                        System.out.println("Enregistrement réussi. Connexion automatique.");
                        this.PSEUDO = pseudo;
                        return true;
                    } else {
                        System.err.println("Échec de l'enregistrement. Veuillez réessayer.");
                    }
                }
            } catch (SQLException e) {
                System.err.println("Erreur de base de données : " + e.getMessage());
                return false; // Échec fatal
            }
        }
    }

    private void clientLoop() {
        Scanner scanner = new Scanner(System.in);
        String choix;

        System.out.println("\nBienvenue, " + PSEUDO + "!");

        while (true) {
            System.out.println("\n--- MENU PRINCIPAL ---");
            System.out.println("1. Lister les salons");
            System.out.println("2. Rejoindre un salon");
            System.out.println("3. Créer un salon");
            System.out.println("4. Quitter l'application");
            System.out.print("Votre choix : ");

            choix = scanner.nextLine();

            try {
                switch (choix) {
                    case "1":
                        System.out.println(chatControlRef.listerSalons());
                        break;
                    case "2":
                        System.out.print("Nom du salon à rejoindre : ");
                        String nomSalon = scanner.nextLine();
                        String ipPort = chatControlRef.rejoindreSalon(nomSalon, PSEUDO);

                        if (ipPort.startsWith("ERROR")) {
                            System.err.println(ipPort);
                        } else {
                            enterChatRoom(nomSalon, ipPort);
                        }
                        break;
                    case "3":
                        System.out.print("Nom du nouveau salon : ");
                        String nouveauNom = scanner.nextLine();
                        chatControlRef.creerSalon(nouveauNom, PSEUDO);
                        System.out.println("Salon '" + nouveauNom + "' créé et démarré (via CORBA).");
                        break;
                    case "4":
                        return;
                    default:
                        System.out.println("Choix invalide.");
                }
            } catch (Exception e) {
                System.err.println("Erreur lors de l'appel CORBA : " + e.getMessage());
            }
        }
    }

    private void enterChatRoom(String nomSalon, String ipPort) {
        String[] parts = ipPort.split(":");
        String ip = parts[0];
        int port = Integer.parseInt(parts[1]);

        try {
            // 1. AFFICHAGE DE L'HISTORIQUE
            System.out.println("\n*** Historique du salon " + nomSalon + " ***");
            String history = DBManager.getSalonHistory(nomSalon);
            System.out.print(history);
            System.out.println("************************************************\n");

            // 2. PHASE 2: Connexion Socket TCP (Messagerie temps réel)
            chatSocket = new Socket(ip, port);
            writer = new PrintWriter(chatSocket.getOutputStream(), true);

            System.out.println("*** Connecté au salon " + nomSalon + " via Socket TCP (" + ipPort + ") ***");
            System.out.println("Taper 'QUITTER' pour revenir au menu.");

            ClientReceiver receiver = new ClientReceiver(chatSocket);
            receiver.start();

            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            String line;

            while ((line = consoleReader.readLine()) != null) {
                if (line.equalsIgnoreCase("QUITTER")) {
                    break;
                }
                writer.println(PSEUDO + ": " + line);
            }

        } catch (IOException e) {
            System.err.println("Erreur de connexion au Socket TCP du salon : " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération de l'historique : " + e.getMessage());
        } finally {
            try {
                if (chatSocket != null && !chatSocket.isClosed()) {
                    chatSocket.close();
                }
                if (chatControlRef != null) {
                    chatControlRef.quitterSalon(nomSalon, PSEUDO);
                }
            } catch (Exception e) {
                System.err.println("Erreur lors de la fermeture de la connexion : " + e.getMessage());
            }
        }
    }
}