package fr.heliocraft.refinr.adapters.in.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Normalisation des noms d'hote comparee a la liste autorisee (le filtre HTTP est teste dans HttpSecurityConfigurationTest). */
class AllowedHostFilterTest {

    @Test
    @DisplayName("Host names are compared without port, case or trailing dot; IPv6 keeps its brackets")
    void hostNamesAreNormalized() {
        assertThat(AllowedHostFilter.hostName("LocalHost:8080")).isEqualTo("localhost");
        assertThat(AllowedHostFilter.hostName("localhost.")).isEqualTo("localhost");
        assertThat(AllowedHostFilter.hostName("127.0.0.1:3000")).isEqualTo("127.0.0.1");
        assertThat(AllowedHostFilter.hostName("[::1]:3000")).isEqualTo("[::1]");
        assertThat(AllowedHostFilter.hostName("::1")).isEqualTo("[::1]");
    }

    @Test
    @DisplayName("The configured list is normalized the same way")
    void configuredHostsAreNormalized() {
        assertThat(new AllowedHostFilter(List.of("LOCALHOST", "::1", "refinr.example.com:443"), null).allowedHosts())
                .containsExactlyInAnyOrder("localhost", "[::1]", "refinr.example.com");
        assertThatThrownBy(() -> new AllowedHostFilter(List.of(), null)).isInstanceOf(IllegalArgumentException.class);
    }
}
