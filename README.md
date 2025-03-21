# Projet de Contrôle à Distance en Java
Logiciel développé dans le cadre du cours Java Avancé (Master 1 GLSI) à l'École Supérieure Polytechnique de Dakar.

## Description
Ce projet implémente un logiciel client-serveur permettant de contrôler un ordinateur à distance via une interface graphique JavaFX. Les fonctionnalités incluent l'exécution de commandes système, l'upload de fichiers, et une connexion sécurisée avec SSL.

## Fonctionnalités
- Communication client-serveur via sockets SSL/TCP.
- Authentification avec login/mot de passe.
- Exécution de commandes Windows via `cmd.exe`.
- Transfert de fichiers du client vers le serveur.
- Interface graphique avec historique et logs.

## Prérequis
- Java 8 ou supérieur.
- Maven (pour gérer les dépendances).
- Fichier `server.keystore` pour SSL.

## Installation
1. Cloner le dépôt : `git clone https://github.com/The-Miller/Projet_ControleOrdinateur.git`
2. Compiler avec Maven : `mvn clean package`
3. Lancer le serveur : `java -cp target/classes hadoop.mapreduce.remotecontrolsoftware.server.Server`
4. Lancer le client : `java -cp target/classes hadoop.mapreduce.remotecontrolsoftware.client.Client`

## Membres du groupe
- Boubacar Niang
- Aissatou Fofana
- Pape Amadou Mandiaye Ndiaye
