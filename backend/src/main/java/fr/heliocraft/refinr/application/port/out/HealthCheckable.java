package fr.heliocraft.refinr.application.port.out;

import fr.heliocraft.refinr.domain.model.ComponentHealth;

import java.util.List;

/**
 * Dependance externe capable de verifier son etat (implementee par les adaptateurs des ports sortants). Ne leve
 * jamais d'exception : un echec est un composant DOWN avec une cause courte, sans secret ni URL.
 */
public interface HealthCheckable {

    /** Etat de chaque composant verifie (un fournisseur de modele peut en declarer deux : analyse et verdict). */
    List<ComponentHealth> health();
}
