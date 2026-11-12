package fr.heliocraft.refinr.domain.service;

import java.util.regex.Pattern;

/** Regle de contenu d'un document source : il doit contenir du texte a analyser. */
public final class DocumentText {

    // Balises de balisage (HTML, XML de stockage) et entites (&nbsp;, &#160;...) : elles ne portent aucun texte utile
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#\\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);");
    private static final Pattern USEFUL = Pattern.compile("[\\p{L}\\p{N}]");

    private DocumentText() {}

    /**
     * Vrai si le document contient au moins une lettre ou un chiffre hors balisage. Un document vide, fait
     * uniquement d'espaces, de balises vides ou d'images n'a rien a analyser : inutile d'appeler le modele.
     */
    public static boolean hasUsefulText(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String text = ENTITY.matcher(TAG.matcher(content).replaceAll(" ")).replaceAll(" ");
        return USEFUL.matcher(text).find();
    }
}
