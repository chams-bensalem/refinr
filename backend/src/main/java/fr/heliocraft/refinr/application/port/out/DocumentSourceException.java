// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.out;

/**
 * Echec d'une lecture ou d'une recherche dans la source documentaire. Le message est sur et utile au client (outil,
 * identifiant du document, statut HTTP, droit manquant si connu) : il ne contient ni URL, ni corps de reponse brut,
 * ni secret.
 */
public class DocumentSourceException extends RuntimeException {

    /** Nature de l'echec, pour le code HTTP renvoye par Refinr. */
    public enum Reason {
        /** Document absent, ou non visible pour le compte de Refinr (souvent indiscernables). */
        NOT_FOUND,
        /** Source injoignable, en erreur, identifiants ou droits du compte refuses, delai depasse. */
        UNAVAILABLE
    }

    private final Reason reason;

    public DocumentSourceException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
