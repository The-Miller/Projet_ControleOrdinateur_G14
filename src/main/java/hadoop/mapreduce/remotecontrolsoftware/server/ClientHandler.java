package hadoop.mapreduce.remotecontrolsoftware.server;

import java.io.*;
import java.net.Socket;

/**
 * Classe qui gère chaque client connecté au serveur.
 * Fonctionne dans un thread séparé pour permettre plusieurs connexions simultanées.
 * Authentifie le client, exécute ses commandes, et gère les uploads de fichiers.
 */
public class ClientHandler implements Runnable {
    private final Socket clientSocket; // Socket du client connecté
    private final Server server; // Référence au serveur principal
    private final String clientAddress; // Adresse IP du client

    /**
     * Constructeur du gestionnaire de client.
     * @param clientSocket Socket du client connecté
     * @param server Référence au serveur
     */
    public ClientHandler(Socket clientSocket, Server server) {
        this.clientSocket = clientSocket; // Initialise le socket
        this.server = server; // Initialise la référence au serveur
        this.clientAddress = clientSocket.getInetAddress().getHostAddress(); // Récupère l’IP du client
    }

    /**
     * Méthode principale exécutée dans un thread.
     * Gère l’authentification et les interactions avec le client.
     */
    @Override
    public void run() {
        try (
            // Initialise les flux de sortie (texte) et d’entrée (lecture) avec fermeture automatique
            PrintWriter out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"))
        ) {
            boolean authenticated = false; // Indicateur d’authentification
            while (!authenticated) { // Boucle jusqu’à authentification réussie ou abandon
                out.println("Entrez votre login (ou 'quit' pour quitter) :"); // Demande le login
                String login = in.readLine(); // Lit le login envoyé par le client
                if (login == null || "quit".equalsIgnoreCase(login)) { // Si connexion fermée ou "quit"
                    out.println("Connexion abandonnée."); // Envoie un message au client
                    server.log("Client " + clientAddress + " a abandonné l’authentification."); // Log l’abandon
                    return; // Quitte la méthode
                }

                out.println("Entrez votre mot de passe :"); // Demande le mot de passe
                String password = in.readLine(); // Lit le mot de passe

                if (server.authenticate(login, password)) { // Vérifie les identifiants
                    authenticated = true; // Marque comme authentifié
                    server.addClient(clientAddress); // Ajoute le client à la liste
                    server.log("Client authentifié et connecté : " + clientAddress); // Log la connexion
                    out.println("Authentification réussie. Vous êtes connecté."); // Confirme au client
                } else {
                    server.log("Échec de l’authentification pour " + clientAddress); // Log l’échec
                    out.println("Authentification échouée. Veuillez réessayer."); // Demande de réessayer
                }
            }

            String command; // Variable pour stocker les commandes
            while ((command = in.readLine()) != null) { // Lit les commandes tant que le client est connecté
                server.log("Commande reçue de " + clientAddress + " : " + command); // Log la commande
                if (command.startsWith("upload:")) { // Si c’est une commande d’upload
                    receiveFile(clientSocket, out, in); // Gère l’upload du fichier
                } else {
                    String result = executeCommand(command); // Exécute la commande système
                    out.println(result); // Envoie le résultat au client
                }
            }
        } catch (IOException e) {
            server.log("Erreur avec le client " + clientAddress + " : " + e.getMessage()); // Log une erreur
        } finally {
            server.removeClient(clientAddress); // Supprime le client de la liste
            try {
                clientSocket.close(); // Ferme le socket du client
            } catch (IOException e) {
                server.log("Erreur lors de la fermeture du socket client : " + e.getMessage()); // Log une erreur
            }
        }
    }

    /**
     * Exécute une commande système sur le serveur.
     * @param command La commande à exécuter
     * @return Le résultat de la commande (stdout ou stderr)
     */
    private String executeCommand(String command) {
        StringBuilder output = new StringBuilder(); // Construit la sortie de la commande
        try {
            // Crée un processus pour exécuter la commande via cmd.exe
            Process process = new ProcessBuilder("cmd.exe", "/c", command).start();
            // Lit la sortie standard (stdout) du processus
            BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line; // Variable pour chaque ligne lue
            while ((line = stdOut.readLine()) != null) { // Lit chaque ligne de stdout
                output.append(line).append("\n"); // Ajoute la ligne au résultat
            }
            // Lit la sortie d’erreur (stderr) du processus
            BufferedReader stdErr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = stdErr.readLine()) != null) { // Lit chaque ligne de stderr
                output.append("ERREUR : ").append(line).append("\n"); // Ajoute l’erreur au résultat
            }
            process.waitFor(); // Attend la fin de l’exécution de la commande
        } catch (IOException | InterruptedException e) {
            output.append("Erreur lors de l'exécution : ").append(e.getMessage()); // Ajoute l’erreur au résultat
        }
        return output.toString().trim(); // Retourne le résultat sans espaces inutiles
    }

    /**
     * Reçoit un fichier envoyé par le client.
     * @param clientSocket Socket du client
     * @param out Flux de sortie vers le client
     * @param in Flux d’entrée depuis le client
     * @throws IOException En cas d’erreur d’entrée/sortie
     */
    private void receiveFile(Socket clientSocket, PrintWriter out, BufferedReader in) throws IOException {
        out.println("SEND_FILE_NAME"); // Demande le nom du fichier
        String fileName = in.readLine(); // Lit le nom envoyé par le client
        out.println("SEND_FILE_SIZE"); // Demande la taille du fichier
        long fileSize = Long.parseLong(in.readLine()); // Lit la taille et convertit en long

        // Log le début de la réception
        server.log("Réception du fichier " + fileName + " (" + fileSize + " octets) depuis " + clientAddress);

        // Ouvre les flux pour recevoir et sauvegarder le fichier
        try (BufferedInputStream fileInput = new BufferedInputStream(clientSocket.getInputStream());
             FileOutputStream fileOutput = new FileOutputStream("received_" + fileName)) {
            byte[] buffer = new byte[8192]; // Buffer de 8KB pour un transfert rapide
            long bytesRead = 0; // Compteur d’octets lus
            int count; // Nombre d’octets lus par itération
            while (bytesRead < fileSize && (count = fileInput.read(buffer)) > 0) { // Tant que pas tout lu
                fileOutput.write(buffer, 0, count); // Écrit les octets dans le fichier
                bytesRead += count; // Met à jour le compteur
            }
            server.log("Fichier " + fileName + " reçu avec succès."); // Log la réussite
        }

        out.println("Fichier reçu et sauvegardé."); // Confirme au client
    }
}
