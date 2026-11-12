package fr.heliocraft.refinr.domain.model;

/** Resultat d'une recherche de documents source : identifiant (a passer a l'analyse), titre et lien de consultation. */
public record DocumentSummary(String id, String title, String url) {}
