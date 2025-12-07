package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

/**
 * Thread dédié à l'écoute des messages provenant du serveur de salon (Socket TCP).
 */
public class ClientReceiver extends Thread {
    private final Socket socket;

    public ClientReceiver(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String message;
            while ((message = reader.readLine()) != null) {
                // Le message en temps réel apparaît juste après le prompt.
                System.out.println("\n[MESSAGE REÇU] " + message);
            }
        } catch (IOException e) {
            System.err.println("Connexion au salon perdue ou fermée.");
        } finally {
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                // Ignorer l'erreur
            }
        }
    }
}