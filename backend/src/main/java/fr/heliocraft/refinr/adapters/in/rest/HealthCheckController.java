package fr.heliocraft.refinr.adapters.in.rest;

import fr.heliocraft.refinr.application.port.in.CheckHealth;
import fr.heliocraft.refinr.domain.model.ComponentHealth;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Etat des dependances externes, verifie par les ports sortants (cf. {@link CheckHealth}) :
 * {@code {"status": "UP"|"DEGRADED", "services": {"confluence": {...}, "jira": {...}, "ai": {...}, "aiVerdict": {...}}}}.
 * Aucune cle ni URL : fournisseur, modele et cause courte uniquement.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthCheckController {

    private final CheckHealth checkHealth;

    public HealthCheckController(CheckHealth checkHealth) {
        this.checkHealth = checkHealth;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        CheckHealth.HealthReport report = checkHealth.check();
        Map<String, Object> services = new LinkedHashMap<>();
        for (ComponentHealth component : report.components()) {
            Map<String, String> service = new LinkedHashMap<>();
            service.put("status", component.up() ? "UP" : "DOWN");
            service.putAll(component.details());
            services.put(component.name(), service);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", report.allUp() ? "UP" : "DEGRADED");
        body.put("services", services);
        return ResponseEntity.ok(body);
    }

    /**
     * Sonde de vivacite (conteneur, orchestrateur) : l'application repond, sans appeler aucune dependance externe
     * (contrairement a {@link #health()}, qui interroge Confluence, Jira et les fournisseurs de modele).
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> live() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
