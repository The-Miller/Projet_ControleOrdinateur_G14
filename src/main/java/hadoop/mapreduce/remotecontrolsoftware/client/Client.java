package hadoop.mapreduce.remotecontrolsoftware.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.nio.file.Files;

/**
 * Classe principale du client pour le logiciel de contrôle à distance.
 * Fournit une interface graphique JavaFX pour se connecter au serveur,
 * envoyer des commandes, uploader des fichiers et afficher les réponses.
 */
public class Client extends Application {
    private TextField commandField; // Champ texte pour saisir les commandes
    private TextArea responseArea; // Zone de texte pour afficher les réponses du serveur
    private ListView<String> commandHistory; // Liste graphique de l’historique des commandes
    private ObservableList<String> historyList; // Liste observable pour l’historique
    private SSLSocket socket; // Socket SSL pour la connexion sécurisée au serveur
    private PrintWriter out; // Flux de sortie pour envoyer des données au serveur
    private BufferedReader in; // Flux d’entrée pour lire les réponses du serveur
    private volatile boolean connected = false; // Indicateur d’état de connexion (thread-safe)
    private volatile boolean uploading = false; // Indicateur d’état d’upload (thread-safe)

    /**
     * Méthode principale de lancement de l’interface client.
     * Configure et affiche l’interface graphique JavaFX.
     */
    @Override
    public void start(Stage primaryStage) {
        // Crée un label pour le titre de l’interface
        Label titleLabel = new Label("Client de Controle a Distance");
        // Applique un style CSS au titre (taille 20px, couleur bleue)
        titleLabel.setStyle("-fx-font-size: 20px; -fx-text-fill: #4a90e2;");

        // Crée un label pour le champ de commande
        Label commandLabel = new Label("Commande");
        commandField = new TextField(); // Crée le champ texte pour les commandes
        commandField.setPrefWidth(300); // Définit une largeur fixe de 300px

        // Crée les boutons de contrôle
        Button sendButton = new Button("Envoyer"); // Bouton pour envoyer une commande
        Button uploadButton = new Button("Uploader"); // Bouton pour uploader un fichier
        Button connectButton = new Button("Se connecter"); // Bouton pour se connecter
        Button disconnectButton = new Button("Se déconnecter"); // Bouton pour se déconnecter
        disconnectButton.setDisable(true); // Désactive "Se déconnecter" par défaut

        // Crée une barre horizontale pour les boutons avec un espacement de 15px
        HBox controlBar = new HBox(15, connectButton, disconnectButton, sendButton, uploadButton);
        controlBar.setPadding(new Insets(10)); // Ajoute un padding de 10px
        // Applique un style CSS à la barre (fond blanc, bordure inférieure)
        controlBar.setStyle("-fx-background-color: #ffffff; -fx-border-color: #d3d3d3; -fx-border-width: 0 0 1 0;");

        // Crée un label pour la zone de réponses
        Label responseLabel = new Label("Réponses du Serveur");
        responseArea = new TextArea(); // Crée la zone de texte pour les réponses
        responseArea.setEditable(false); // Rend la zone non éditable
        responseArea.setPrefHeight(250); // Définit une hauteur fixe de 250px
        // Crée un conteneur vertical pour le label et la zone de réponses
        VBox responsePane = new VBox(10, responseLabel, responseArea);
        responsePane.setPadding(new Insets(10)); // Ajoute un padding de 10px

        // Crée un label pour l’historique des commandes
        Label historyLabel = new Label("Historique des Commandes");
        commandHistory = new ListView<>(); // Crée la liste graphique de l’historique
        historyList = FXCollections.observableArrayList(); // Liste observable pour l’historique
        commandHistory.setItems(historyList); // Associe la liste à la ListView
        commandHistory.setPrefWidth(200); // Définit une largeur fixe de 200px
        // Crée un conteneur vertical pour le label et l’historique
        VBox historyPane = new VBox(10, historyLabel, commandHistory);
        historyPane.setPadding(new Insets(10)); // Ajoute un padding de 10px

        // Crée une mise en page horizontale pour les réponses et l’historique
        HBox mainLayout = new HBox(15, responsePane, historyPane);
        // Crée une section verticale pour le champ de commande et les boutons
        VBox commandSection = new VBox(10, commandLabel, commandField, controlBar);
        // Crée la mise en page globale avec titre, section commande et contenu principal
        VBox root = new VBox(15, titleLabel, commandSection, mainLayout);
        root.setPadding(new Insets(15)); // Ajoute un padding de 15px

        // Définit l’action du bouton "Envoyer"
        sendButton.setOnAction(event -> sendCommand());
        // Définit l’action du bouton "Uploader" (exécuté dans le thread JavaFX)
        uploadButton.setOnAction(event -> Platform.runLater(() -> uploadFile(primaryStage)));
        // Définit l’action du bouton "Se connecter"
        connectButton.setOnAction(event -> {
            if (!connected) { // Vérifie si pas déjà connecté
                connectToServer(); // Tente la connexion
                if (connected) { // Si connexion réussie
                    connectButton.setDisable(true); // Désactive "Se connecter"
                    disconnectButton.setDisable(false); // Active "Se déconnecter"
                }
            }
        });
        // Définit l’action du bouton "Se déconnecter"
        disconnectButton.setOnAction(event -> {
            if (connected) { // Vérifie si connecté
                disconnectFromServer(); // Déconnecte
                connectButton.setDisable(false); // Réactive "Se connecter"
                disconnectButton.setDisable(true); // Désactive "Se déconnecter"
            }
        });

        // Crée la scène principale avec une taille de 700x500px
        Scene scene = new Scene(root, 700, 500);
        // Charge le fichier CSS pour styliser l’interface
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        primaryStage.setTitle("Client de Controle a Distance"); // Définit le titre de la fenêtre
        primaryStage.setScene(scene); // Associe la scène à la fenêtre
        primaryStage.show(); // Affiche la fenêtre
    }

    /**
     * Établit une connexion sécurisée au serveur avec authentification.
     */
    private void connectToServer() {
        try {
            // Définit le truststore SSL pour valider le certificat du serveur
            System.setProperty("javax.net.ssl.trustStore", "server.keystore");
            System.setProperty("javax.net.ssl.trustStorePassword", "password");
            // Récupère une factory pour créer des sockets SSL
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            // Crée un socket client SSL connecté à localhost:12345
            socket = (SSLSocket) factory.createSocket("localhost", 12345);

            // Initialise les flux de sortie (texte) et d’entrée (lecture)
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            while (!connected) { // Boucle jusqu’à connexion réussie ou abandon
                String[] credentials = showLoginDialog(); // Affiche la boîte de dialogue d’authentification
                if (credentials == null || "quit".equalsIgnoreCase(credentials[0])) { // Si annulé ou "quit"
                    log("Connexion abandonnee."); // Log l’abandon
                    socket.close(); // Ferme le socket
                    return; // Quitte la méthode
                }

                String serverResponse = in.readLine(); // Lit la demande de login
                log(serverResponse); // Affiche la demande dans l’interface
                out.println(credentials[0]); // Envoie le login
                serverResponse = in.readLine(); // Lit la demande de mot de passe
                log(serverResponse); // Affiche la demande
                out.println(credentials[1]); // Envoie le mot de passe

                serverResponse = in.readLine(); // Lit la réponse d’authentification
                log(serverResponse); // Affiche la réponse
                if (serverResponse.contains("Authentification reussie")) { // Si authentifié
                    connected = true; // Marque comme connecté
                    new Thread(this::listenToServer).start(); // Lance un thread pour écouter le serveur
                    break; // Sort de la boucle
                } else {
                    log("Voulez-vous reessayer ?"); // Invite à réessayer
                }
            }
        } catch (IOException e) {
            log("Erreur de connexion SSL : " + e.getMessage()); // Log une erreur de connexion
        }
    }

    /**
     * Affiche une boîte de dialogue pour saisir les identifiants.
     * @return Tableau avec login et mot de passe, ou null si annulé
     */
    private String[] showLoginDialog() {
        Dialog<String[]> dialog = new Dialog<>(); // Crée une boîte de dialogue
        dialog.setTitle("Authentification"); // Définit le titre
        dialog.setHeaderText("Entrez vos identifiants"); // Définit l’en-tête

        // Ajoute un bouton "Connexion" pour valider
        ButtonType loginButtonType = new ButtonType("Connexion", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL); // Ajoute "Connexion" et "Annuler"

        // Crée un conteneur vertical pour les champs de saisie
        VBox vbox = new VBox(10);
        TextField loginField = new TextField(); // Champ pour le login
        loginField.setPromptText("Login (ou 'quit' pour quitter)"); // Texte indicatif
        PasswordField passwordField = new PasswordField(); // Champ pour le mot de passe (masqué)
        passwordField.setPromptText("Mot de passe"); // Texte indicatif
        // Ajoute les labels et champs au conteneur
        vbox.getChildren().addAll(new Label("Login:"), loginField, new Label("Mot de passe:"), passwordField);

        dialog.getDialogPane().setContent(vbox); // Associe le conteneur à la boîte de dialogue
        // Convertit les saisies en tableau si "Connexion" est cliqué
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) { // Si clic sur "Connexion"
                return new String[]{loginField.getText(), passwordField.getText()}; // Retourne login et mot de passe
            }
            return null; // Retourne null si annulé
        });

        return dialog.showAndWait().orElse(null); // Affiche et attend une réponse, null par défaut si pas de résultat
    }

    /**
     * Déconnecte le client du serveur.
     */
    private void disconnectFromServer() {
        try {
            if (socket != null && !socket.isClosed()) { // Vérifie si le socket existe et est ouvert
                socket.close(); // Ferme le socket
            }
            connected = false; // Marque comme déconnecté
            if (uploading) { // Si un upload est en cours
                log("Deconnexion pendant l’upload. Envoi annule."); // Log l’interruption
            } else {
                log("Deconnecte du serveur."); // Log la déconnexion normale
            }
        } catch (IOException e) {
            log("Erreur lors de la deconnexion : " + e.getMessage()); // Log une erreur
        }
    }

    /**
     * Envoie une commande au serveur.
     */
    private void sendCommand() {
        if (!connected) { // Vérifie si connecté
            log("Erreur : Vous devez etre connecte pour envoyer une commande."); // Log une erreur
            return; // Quitte la méthode
        }
        String command = commandField.getText(); // Récupère la commande saisie
        if (command != null && !command.trim().isEmpty()) { // Vérifie qu’elle n’est pas vide
            out.println(command); // Envoie la commande au serveur
            historyList.add(command); // Ajoute à l’historique
            commandField.clear(); // Vide le champ texte
        }
    }

    /**
     * Uploade un fichier vers le serveur dans un thread séparé.
     * @param stage Fenêtre principale pour afficher le sélecteur de fichier
     */
    private void uploadFile(Stage stage) {
        if (!connected) { // Vérifie si connecté
            log("Erreur : Vous devez etre connecte pour uploader un fichier."); // Log une erreur
            return; // Quitte la méthode
        }
        FileChooser fileChooser = new FileChooser(); // Crée un sélecteur de fichier
        fileChooser.setTitle("Choisir un fichier a envoyer"); // Définit le titre du sélecteur
        File file = fileChooser.showOpenDialog(stage); // Ouvre le sélecteur et récupère le fichier choisi
        if (file != null) { // Si un fichier est sélectionné
            new Thread(() -> { // Lance l’upload dans un thread séparé
                uploading = true; // Indique qu’un upload est en cours
                try {
                    log("Debut de l’envoi du fichier : " + file.getName()); // Log le début
                    out.println("upload:" + file.getAbsolutePath()); // Envoie la commande upload avec chemin

                    String response = in.readLine(); // Lit la demande de nom de fichier
                    if ("SEND_FILE_NAME".equals(response)) { // Si serveur demande le nom
                        out.println(file.getName()); // Envoie le nom du fichier
                    }
                    response = in.readLine(); // Lit la demande de taille
                    if ("SEND_FILE_SIZE".equals(response)) { // Si serveur demande la taille
                        long fileSize = Files.size(file.toPath()); // Calcule la taille du fichier
                        out.println(fileSize); // Envoie la taille

                        // Ouvre les flux pour envoyer le fichier
                        try (BufferedOutputStream fileOutput = new BufferedOutputStream(socket.getOutputStream());
                             FileInputStream fileInput = new FileInputStream(file)) {
                            byte[] buffer = new byte[8192]; // Buffer de 8KB pour un transfert rapide
                            int bytesRead; // Nombre d’octets lus
                            while (connected && (bytesRead = fileInput.read(buffer)) > 0) { // Tant que connecté et données à lire
                                fileOutput.write(buffer, 0, bytesRead); // Écrit les octets dans le flux
                            }
                            fileOutput.flush(); // Vide le buffer pour garantir l’envoi
                        }

                        if (connected) { // Si toujours connecté
                            response = in.readLine(); // Lit la confirmation du serveur
                            log(response); // Affiche la confirmation
                        }
                    }
                } catch (IOException e) {
                    if (connected) { // Si erreur et encore connecté
                        log("Erreur lors de l’envoi du fichier : " + e.getMessage()); // Log l’erreur
                    } else {
                        log("Envoi interrompu par deconnexion."); // Log l’interruption
                    }
                } finally {
                    uploading = false; // Réinitialise l’indicateur d’upload
                }
            }).start(); // Démarre le thread
        }
    }

    /**
     * Écoute les réponses du serveur dans un thread séparé.
     */
    private void listenToServer() {
        try {
            String response; // Variable pour stocker les réponses
            while (connected && (response = in.readLine()) != null) { // Tant que connecté et données à lire
                // Ignore les messages de protocole d’upload
                if (!"SEND_FILE_NAME".equals(response) && !"SEND_FILE_SIZE".equals(response)) {
                    log("Reponse du serveur : " + response); // Affiche la réponse
                }
            }
        } catch (IOException e) {
            if (connected) { // Si erreur et encore connecté
                log("Erreur de lecture : " + e.getMessage()); // Log l’erreur
            }
        }
    }

    /**
     * Ajoute un message à la zone de réponses (thread-safe).
     * @param message Le message à afficher
     */
    private void log(String message) {
        // Ajoute le message dans le thread JavaFX pour éviter les conflits
        javafx.application.Platform.runLater(() -> responseArea.appendText(message + "\n"));
    }

    /**
     * Méthode appelée à la fermeture de l’application.
     * Déconnecte le client.
     */
    @Override
    public void stop() {
        disconnectFromServer(); // Déconnecte le client
    }

    /**
     * Point d’entrée de l’application JavaFX.
     * @param args Arguments de la ligne de commande
     */
    public static void main(String[] args) {
        launch(args); // Lance l’application JavaFX
    }
}
