// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.out;

/**
 * Echec d'une operation du gestionnaire de tickets.
 * <ul>
 *   <li>{@link #getMessage()} : message sur, seul a figurer dans les logs et les traces (operation, statut HTTP, codes
 *   et champs en cause) ; jamais de contenu de ticket, de corps de reponse, d'URL ni de secret ;</li>
 *   <li>{@link #clientMessage()} : renvoye seulement au client dans la reponse de creation ({@code failed[].reason}) ;
 *   il peut ajouter les messages de validation du gestionnaire, qui citent parfois le contenu envoye (label, titre).</li>
 * </ul>
 * Jamais de cause : les exceptions WebClient portent l'URL, les en-tetes de la requete et le corps de la reponse, et
 * celles de Jackson citent le texte lu.
 */
public class IssueTrackerException extends RuntimeException {

    private final String clientMessage;

    public IssueTrackerException(String message) {
        this(message, message);
    }

    public IssueTrackerException(String message, String clientMessage) {
        super(message);
        this.clientMessage = clientMessage != null ? clientMessage : message;
    }

    public String clientMessage() {
        return clientMessage;
    }
}
