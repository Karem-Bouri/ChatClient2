package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class ClientReceiver extends Thread {

    private final Socket socket;

    public ClientReceiver(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            // Lit les messages du Socket du salon tant que le socket est ouvert
            while ((line = reader.readLine()) != null) {
                // Affiche le message reçu
                System.out.println("\n" + line + "\nVotre message (QUITTER pour sortir) : ");
            }
        } catch (IOException e) {
            // Le thread se termine naturellement à la coupure de la connexion.
        } finally {
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}