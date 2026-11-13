// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Cache des verdicts, par empreinte du contenu juge (titre, description, criteres) et du fournisseur, du modele et
 * de la version du prompt. Seuls les verdicts reussis sont gardes (jamais "indisponible" ni une erreur) ; seuls des
 * verdicts produits par le serveur y entrent, le client ne peut donc rien imposer. Une story modifiee depuis
 * l'apercu a une autre empreinte et est rejugee. En memoire, borne (LRU) et avec une duree de vie.
 * La reevaluation avec une precision de l'utilisateur ({@link #reassess}) n'est jamais mise en cache.
 */
public class CachingReadinessAssessor implements ReadinessAssessorPort {

    private static final Logger log = LoggerFactory.getLogger(CachingReadinessAssessor.class);

    private final ReadinessAssessorPort delegate;
    private final String fingerprint;
    private final long ttlNanos;
    private final LongSupplier nanoTime;
    private final Map<String, Cached> entries;

    private record Cached(Readiness verdict, long storedAt) {}

    public CachingReadinessAssessor(LlmReadinessAssessor delegate, Duration ttl, int maxEntries) {
        this(delegate, delegate.fingerprint(), ttl, maxEntries, System::nanoTime);
    }

    CachingReadinessAssessor(ReadinessAssessorPort delegate, String fingerprint, Duration ttl, int maxEntries,
                             LongSupplier nanoTime) {
        this.delegate = delegate;
        this.fingerprint = fingerprint;
        this.ttlNanos = ttl.toNanos();
        this.nanoTime = nanoTime;
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
                return size() > maxEntries;
            }
        };
    }

    @Override
    public List<Readiness> assess(List<Story> stories) {
        List<Readiness> verdicts = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        List<Integer> misses = new ArrayList<>();
        for (int i = 0; i < stories.size(); i++) {
            String key = key(stories.get(i));
            keys.add(key);
            Readiness cached = get(key);
            verdicts.add(cached);
            if (cached == null) {
                misses.add(i);
            }
        }
        log.info("Readiness verdicts: {} from cache, {} to assess", stories.size() - misses.size(), misses.size());
        if (misses.isEmpty()) {
            return verdicts;
        }
        List<Readiness> fresh = delegate.assess(misses.stream().map(stories::get).toList());
        for (int j = 0; j < misses.size(); j++) {
            Readiness verdict = j < fresh.size() ? fresh.get(j) : null;
            int index = misses.get(j);
            verdicts.set(index, verdict);
            if (verdict != null) {
                put(keys.get(index), verdict);
            }
        }
        return verdicts;
    }

    @Override
    public Readiness reassess(String summary, String description, String precision) {
        return delegate.reassess(summary, description, precision);
    }

    private synchronized Readiness get(String key) {
        Cached entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (nanoTime.getAsLong() - entry.storedAt() >= ttlNanos) {
            entries.remove(key);
            return null;
        }
        return entry.verdict();
    }

    private synchronized void put(String key, Readiness verdict) {
        entries.put(key, new Cached(verdict, nanoTime.getAsLong()));
    }

    /** SHA-256 du contenu juge et de l'empreinte du fournisseur (separateurs non ambigus). */
    String key(Story story) {
        StringBuilder content = new StringBuilder(fingerprint).append('\u0000')
                .append(nullToEmpty(story.getTitle())).append('\u0000')
                .append(nullToEmpty(story.getDescription())).append('\u0000');
        if (story.getAcceptanceCriteria() != null) {
            story.getAcceptanceCriteria().forEach(c -> content.append(nullToEmpty(c)).append('\u0001'));
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
