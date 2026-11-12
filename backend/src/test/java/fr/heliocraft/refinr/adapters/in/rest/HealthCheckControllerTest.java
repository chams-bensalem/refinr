package fr.heliocraft.refinr.adapters.in.rest;

import fr.heliocraft.refinr.application.port.in.CheckHealth;
import fr.heliocraft.refinr.domain.model.ComponentHealth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Health check : etat de chaque dependance tel que rapporte par les ports, sans WebClient dans le controleur. */
@WebMvcTest(HealthCheckController.class)
class HealthCheckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckHealth checkHealth;

    @Test
    @DisplayName("Every component is listed with its status and safe details; one DOWN component means DEGRADED")
    void reportsEveryComponent() throws Exception {
        when(checkHealth.check()).thenReturn(new CheckHealth.HealthReport(false, List.of(
                ComponentHealth.down("confluence", "HTTP 401", Map.of()),
                ComponentHealth.up("jira", Map.of()),
                ComponentHealth.up("ai", Map.of("provider", "openai-compatible", "model", "qwen3:8b")),
                ComponentHealth.down("aiVerdict", "unreachable", Map.of("provider", "google", "model", "gemini-2.5-flash")))));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.services.confluence.status").value("DOWN"))
                .andExpect(jsonPath("$.services.confluence.error").value("HTTP 401"))
                .andExpect(jsonPath("$.services.jira.status").value("UP"))
                .andExpect(jsonPath("$.services.ai.status").value("UP"))
                .andExpect(jsonPath("$.services.ai.provider").value("openai-compatible"))
                .andExpect(jsonPath("$.services.ai.model").value("qwen3:8b"))
                .andExpect(jsonPath("$.services.aiVerdict.error").value("unreachable"));
    }

    @Test
    @DisplayName("All components UP means UP")
    void allUp() throws Exception {
        when(checkHealth.check()).thenReturn(new CheckHealth.HealthReport(true, List.of(ComponentHealth.up("jira", Map.of()))));

        mockMvc.perform(get("/api/v1/health")).andExpect(jsonPath("$.status").value("UP"));
    }
}
