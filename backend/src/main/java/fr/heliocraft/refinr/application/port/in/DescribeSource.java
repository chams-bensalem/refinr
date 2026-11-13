// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

/** Source documentaire active (une seule par instance, choisie par configuration) : nom et format des espaces. */
public interface DescribeSource {

    /** Nom affichable de la source active. */
    String displayName();

    /** Vrai si {@code space} a le format d'un espace de la source active (null ou vide : faux). */
    boolean acceptsSpace(String space);

    /** Adresse web du document dans la source active, ou null (identifiant invalide ou adresse inconnue). Sans appel. */
    String documentUrl(String documentId);
}
