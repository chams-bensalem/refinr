# Décisions techniques

Une ligne par choix structurant : la décision, puis sa justification.

## Backend

- **Java 25** (LTS) : version imposée par le projet ; `java.version=25`, compilation en `--release 25`.
- **Spring Boot 4.1.2** (Spring Framework 7.0) : dernière version stable ; seule ligne de Boot pleinement supportée sur Java 25 à long terme (3.5.x en fin de support).
- **Starters modulaires de Boot 4** (`spring-boot-starter-webmvc`, `spring-boot-starter-webclient`, `spring-boot-starter-webmvc-test`) : remplacent `starter-web` (déprécié) et `starter-webflux` (seul WebClient est utilisé, pas le serveur réactif).
- **Résilience native de Spring Framework 7** (`org.springframework.resilience.annotation.Retryable` + `@EnableResilientMethods`) au lieu de spring-retry : spring-retry n'est plus géré par Boot 4, est en maintenance et compilé contre Spring 6.2 ; supprime deux dépendances (spring-retry, spring-aspects). Transposition à l'identique (`maxAttempts=3` → `maxRetries=2`).
- **Jackson 3** (`tools.jackson`, géré par Boot 4) : plus aucun `com.fasterxml.jackson.core:jackson-databind` ; seul `jackson-annotations` (partagé par Jackson 3) subsiste. Le mapper local de l'analyseur utilise `JsonMapper.builderWithJackson2Defaults()` pour garder le comportement de parsing de Jackson 2.
- **`spring.jackson.use-jackson2-defaults=true` — PROVISOIRE** : garde les défauts Jackson 2 de Spring Boot pour la sérialisation REST pendant la montée de version (aucun changement fonctionnel) ; **retrait prévu en phase 4**, avec vérification du contrat JSON.
- **logstash-logback-encoder 9.0** : première version basée sur Jackson 3, évite de réintroduire Jackson 2 pour les logs.
- **MockWebServer 5.5.1 (artefact `mockwebserver`, package `okhttp3.mockwebserver`)** : version explicite car Boot 4 ne gère plus OkHttp ; artefact de compatibilité choisi pour ne pas réécrire les tests pendant la montée de version (migration vers `mockwebserver3` possible plus tard).
- **Mockito chargé en `-javaagent`** (surefire + `dependency:properties`) : le chargement dynamique d'agent est déprécié depuis Java 21 ; remplace l'option `-Dnet.bytebuddy.experimental`.
- **Suppression de `DocumentAnalyzer`** : code mort (aucune référence, cf. AUDIT) ; supprimé plutôt que migré vers Jackson 3. Seul effet visible : le log de démarrage « Using Ollama » de ce bean disparaît.
- **ArchUnit 1.4.1 (`archunit-junit5`, scope test)** : tests d'architecture hexagonale exécutés par JUnit 5 (domaine/application sans framework, dépendances vers le centre, adaptateurs indépendants, aucun identifiant `jira` au centre) ; bibliothèque de test seule, sans impact sur l'artefact livré.
- **Cas d'usage câblés par `config/UseCaseConfig` (`@Bean`)** : les services de `application/service` restent du Java pur, sans `@Service`, conformément à la règle « domaine et application sans Spring ».
- **`SearchDocuments` renvoie des `DocumentSummary(id, title, url)`** (type de domaine) : remplace la `Map` brute de Confluence (dette de la phase 3) ; `GET /confluence/search` répond `{success, results:[{id,title,url}]}` et ne demande plus le contenu des pages (`expand=body.storage` retiré). Contrat REST modifié, cf. `CHANGELOG.md`.
- **Retry : la création Jira n'est jamais retentée, réponse partielle à la place** : POST non idempotent (un `500` ou un délai dépassé peut survenir après la création côté Jira → doublon). Un échec est signalé dans la réponse (`created` / `failed`, `207`/`502`) et la création continue pour les autres éléments ; l'utilisateur relance uniquement ce qui a échoué. Lecture Confluence et analyse LLM restent retentées (`@Retryable`, Spring 7 suit les causes), comportement figé par `ApplicationWiringTest`.
- **`IssueTrackerException` dans `application/port/out`** : exception du contrat du port (levée par l'adaptateur, lue par `CreateBacklogService`), au message sûr pour le client ; la règle ArchUnit « ports sortants = interfaces » admet désormais les exceptions de ces ports.

## Frontend

- **Mise à jour prudente** : Vite 5 → 8 (et `@vitejs/plugin-react` 6) et mises à jour mineures/correctives (axios, react-router 6.30.x, react-query, postcss…) pour corriger `npm audit` ; **React 18, React Router 6, Tailwind 3 et TypeScript 5.9 conservés** — le frontend n'a aucun test pour sécuriser des montées majeures touchant rendu et routage, et TypeScript 7 (portage natif) est une rupture d'outillage.
- **Node figé** (`.nvmrc` + `engines` dans `package.json`) : Node 26.12.1 (LTS) dans `.nvmrc` à la racine = version de référence pour le développement et la CI ; `engines` assoupli à node `>=22.12.0` / npm `>=10.9.0` (minimum requis par Vite 8, Node 20 étant en fin de vie) pour ne pas bloquer les contributeurs sur une LTS plus ancienne.
- **`npm audit` : 2 alertes modérées acceptées** (react-router 6 : open redirect via backslash dans `<Link>`/`useNavigate`, et injection lors de l'hydratation SSR) — corrigées seulement en v7 ; non exploitables ici (chemins de navigation statiques internes, pas de SSR). À lever lors de la future montée React Router 7.
- **ESLint 10 (config « flat » existante) + `typescript-eslint`, `eslint-plugin-react-hooks`, `eslint-plugin-react-refresh`, `globals`, `@eslint/js` en devDependencies** : dépendances que `eslint.config.js` importait déjà sans qu'elles soient déclarées (lint impossible) ; option `--ext` retirée du script (inexistante en config flat).
- **`overrides.minimatch: ^11.0.8`** : la chaîne ESLint 10 / typescript-eslint tire `minimatch` 10.x (GHSA-g3hv-75wg-m249, DoS) ; npm ne proposait qu'un retour à ESLint 9. Outillage de dev uniquement ; lint vérifié fonctionnel avec minimatch 11. À retirer quand ESLint en dépendra nativement.
