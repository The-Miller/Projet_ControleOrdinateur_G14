package hadoop.mapreduce.remotecontrolsoftware.server;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;


/**
 * Classe principale du serveur pour le logiciel de contrôle à distance.
 * Gère une interface graphique JavaFX, écoute les connexions clients via SSL,
 * et journalise les événements dans une interface et un fichier.
 */
public class Server extends Application {
    private TextArea logArea; // Zone de texte pour afficher les logs dans l'interface
    private ListView<String> clientListView; // Liste graphique des clients connectés
    private SSLServerSocket serverSocket; // Socket sécurisé pour écouter les connexions
    private boolean running = false; // Indicateur d'état du serveur (démarré ou arrêté)
    private final Set<String> connectedClients = new HashSet<>(); // Ensemble des adresses IP des clients connectés
    private ObservableList<String> clientObservableList; // Liste observable pour l'interface graphique
    private PrintWriter logFileWriter; // Writer pour sauvegarder les logs dans un fichier

    /**
     * Méthode principale de lancement de l'application JavaFX.
     * Configure et affiche l'interface graphique du serveur.
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            // Forcer UTF-8 pour le fichier de logs
            logFileWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream("server_log.txt", true), "UTF-8"), true);
        } catch (IOException e) {
            // Affiche une erreur en console si le fichier ne peut pas être créé
            System.err.println("Erreur lors de l’initialisation du fichier de log : " + e.getMessage());
        }

        // Crée un label pour le titre de l'interface
        Label titleLabel = new Label("Serveur de Contrôle à Distance");
        // Applique un style CSS au titre (taille 20px, couleur bleue)
        titleLabel.setStyle("-fx-font-size: 20px; -fx-text-fill: #4a90e2;");

        // Crée le bouton pour démarrer le serveur
        Button startButton = new Button("Démarrer");
        // Crée le bouton pour arrêter le serveur, désactivé par défaut
        Button stopButton = new Button("Arrêter");
        stopButton.setDisable(true); // Désactive le bouton "Arrêter" au démarrage

        // Définit l’action du bouton "Démarrer"
        startButton.setOnAction(event -> {
            if (!running) { // Vérifie si le serveur n’est pas déjà en marche
                new Thread(this::startServer).start(); // Lance le serveur dans un thread séparé
                startButton.setDisable(true); // Désactive "Démarrer" après clic
                stopButton.setDisable(false); // Active "Arrêter"
            }
        });

        // Définit l’action du bouton "Arrêter"
        stopButton.setOnAction(event -> {
            if (running) { // Vérifie si le serveur est en marche
                stopServer(); // Arrête le serveur
                startButton.setDisable(false); // Réactive "Démarrer"
                stopButton.setDisable(true); // Désactive "Arrêter"
            }
        });

        // Crée une barre horizontale pour les boutons avec un espacement de 15px
        HBox controlBar = new HBox(15, startButton, stopButton);
        controlBar.setPadding(new Insets(10)); // Ajoute un padding de 10px
        // Applique un style CSS à la barre (fond blanc, bordure inférieure)
        controlBar.setStyle("-fx-background-color: #ffffff; -fx-border-color: #d3d3d3; -fx-border-width: 0 0 1 0;");

        // Crée un label pour la liste des clients
        Label clientsLabel = new Label("Clients Connectés");
        clientListView = new ListView<>(); // Crée la liste graphique des clients
        clientObservableList = FXCollections.observableArrayList(); // Liste observable liée à l’interface
        clientListView.setItems(clientObservableList); // Associe la liste observable à la ListView
        clientListView.setPrefWidth(200); // Définit une largeur fixe de 200px
        // Crée un conteneur vertical pour le label et la liste avec un espacement de 10px
        VBox clientsPane = new VBox(10, clientsLabel, clientListView);
        clientsPane.setPadding(new Insets(10)); // Ajoute un padding de 10px

        // Crée un label pour la zone de logs
        Label logLabel = new Label("Journal des Événements");
        logArea = new TextArea(); // Crée la zone de texte pour les logs
        logArea.setEditable(false); // Rend la zone non éditable
        logArea.setPrefHeight(300); // Définit une hauteur fixe de 300px
        // Crée un conteneur vertical pour le label et la zone de logs avec un espacement de 10px
        VBox logPane = new VBox(10, logLabel, logArea);
        logPane.setPadding(new Insets(10)); // Ajoute un padding de 10px
        logPane.setPrefWidth(400); // Définit une largeur fixe de 400px

        // Crée une mise en page horizontale pour les logs et la liste des clients
        HBox mainLayout = new HBox(15, logPane, clientsPane);
        mainLayout.setPadding(new Insets(15)); // Ajoute un padding de 15px
        // Crée une mise en page verticale globale avec titre, barre et contenu principal
        VBox root = new VBox(15, titleLabel, controlBar, mainLayout);

        // Crée la scène principale avec une taille de 700x500px
        Scene scene = new Scene(root, 700, 500);
        // Charge le fichier CSS pour styliser l’interface
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        primaryStage.setTitle("Serveur de Contrôle à Distance"); // Définit le titre de la fenêtre
        primaryStage.setScene(scene); // Associe la scène à la fenêtre
        primaryStage.show(); // Affiche la fenêtre
    }

    /**
     * Démarre le serveur SSL pour écouter les connexions clients.
     * Fonctionne dans un thread séparé pour ne pas bloquer l’interface.
     */
    private void startServer() {
        try {
            // Définit le keystore SSL pour sécuriser les connexions
            System.setProperty("javax.net.ssl.keyStore", "server.keystore");
            System.setProperty("javax.net.ssl.keyStorePassword", "password");
            // Récupère une factory pour créer des sockets SSL
            SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            // Crée un socket serveur SSL sur le port 12345
            serverSocket = (SSLServerSocket) factory.createServerSocket(12345);

            running = true; // Indique que le serveur est actif
            log("Serveur SSL démarré sur le port 12345. En attente de connexions sécurisées...");

            while (running) { // Boucle tant que le serveur est actif
                Socket clientSocket = serverSocket.accept(); // Accepte une connexion client
                ClientHandler clientHandler = new ClientHandler(clientSocket, this); // Crée un gestionnaire pour le client
                new Thread(clientHandler).start(); // Lance un thread pour gérer ce client
            }
        } catch (IOException e) {
            if (running) { // Si erreur et serveur actif, log l’erreur
                log("Erreur serveur SSL : " + e.getMessage());
            }
        }
    }

    /**
     * Arrête le serveur et ferme les connexions.
     */
    private void stopServer() {
        running = false; // Indique que le serveur doit s’arrêter
        try {
            if (serverSocket != null && !serverSocket.isClosed()) { // Vérifie si le socket existe et est ouvert
                serverSocket.close(); // Ferme le socket serveur
            }
            log("Serveur arrêté."); // Log l’arrêt
            clearClients(); // Vide la liste des clients
        } catch (IOException e) {
            log("Erreur lors de la fermeture : " + e.getMessage()); // Log une éventuelle erreur
        }
    }

    /**
     * Ajoute un message au journal graphique et au fichier de logs.
     * @param message Le message à journaliser
     */
    public void log(String message) {
        // Crée un timestamp au format "année-mois-jour heure:minute:seconde"
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String logEntry = "[" + timestamp + "] " + message; // Formate le message avec timestamp
        // Ajoute le message à l’interface graphique (thread-safe via Platform.runLater)
        javafx.application.Platform.runLater(() -> logArea.appendText(logEntry + "\n"));
        if (logFileWriter != null) { // Si le writer existe
            logFileWriter.println(logEntry); // Écrit dans le fichier de logs
        }
    }

    /**
     * Ajoute un client à la liste des connectés.
     * @param clientAddress L’adresse IP du client
     */
    public void addClient(String clientAddress) {
        // Met à jour la liste dans le thread JavaFX pour éviter les conflits
        javafx.application.Platform.runLater(() -> {
            connectedClients.add(clientAddress); // Ajoute l’IP à l’ensemble
            clientObservableList.setAll(connectedClients); // Met à jour la liste graphique
        });
    }

    /**
     * Supprime un client de la liste des connectés.
     * @param clientAddress L’adresse IP du client
     */
    public void removeClient(String clientAddress) {
        // Met à jour la liste dans le thread JavaFX
        javafx.application.Platform.runLater(() -> {
            connectedClients.remove(clientAddress); // Supprime l’IP de l’ensemble
            clientObservableList.setAll(connectedClients); // Met à jour la liste graphique
        });
    }

    /**
     * Vide la liste des clients connectés.
     */
    private void clearClients() {
        // Vide la liste dans le thread JavaFX
        javafx.application.Platform.runLater(() -> {
            connectedClients.clear(); // Supprime tous les clients de l’ensemble
            clientObservableList.clear(); // Vide la liste graphique
        });
    }

    /**
     * Vérifie les identifiants d’authentification.
     * @param login Login saisi
     * @param password Mot de passe saisi
     * @return true si authentification réussie, false sinon
     */
    public boolean authenticate(String login, String password) {
        return "bouba".equals(login) && "passer".equals(password); // Vérifie login/password fixes
    }

    /**
     * Méthode appelée à la fermeture de l’application.
     * Arrête le serveur et ferme le fichier de logs.
     */
    @Override
    public void stop() {
        stopServer(); // Arrête le serveur
        if (logFileWriter != null) { // Si le writer existe
            logFileWriter.close(); // Ferme le fichier de logs
        }
    }

    /**
     * Point d’entrée de l’application JavaFX.
     * @param args Arguments de la ligne de commande
     */
    public static void main(String[] args) {
        launch(args); // Lance l’application JavaFX
    }
}
