package fr.heliocraft.refinr.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Locale;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Regles de l'architecture hexagonale (cf. CLAUDE.md) : domaine et application sans framework,
 * dependances orientees vers le centre, adaptateurs independants, aucun vocabulaire Jira au centre.
 */
@AnalyzeClasses(packages = "fr.heliocraft.refinr", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String ROOT = "fr.heliocraft.refinr";
    private static final String DOMAIN = ROOT + ".domain..";
    private static final String APPLICATION = ROOT + ".application..";
    private static final String ADAPTERS = ROOT + ".adapters..";
    private static final String CONFIG = ROOT + ".config..";

    @ArchTest
    static final ArchRule domain_depends_only_on_the_jdk = classes()
            .that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", DOMAIN)
            .because("le domaine est du Java pur : ni Spring, ni WebClient, ni Jackson");

    @ArchIgnore(reason = "active a l'etape 4 (services applicatifs)")
    @ArchTest
    static final ArchRule application_depends_only_on_the_jdk_slf4j_and_domain = classes()
            .that().resideInAPackage(APPLICATION)
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "org.slf4j..", DOMAIN, APPLICATION)
            .because("l'application ne connait que le domaine et ses ports (SLF4J autorise : simple facade de logs)");

    @ArchIgnore(reason = "active a l'etape 5 (adaptateur REST)")
    @ArchTest
    static final ArchRule dependencies_point_inwards = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy(DOMAIN)
            .layer("Application").definedBy(APPLICATION)
            .layer("Adapters").definedBy(ADAPTERS)
            .layer("Config").definedBy(CONFIG)
            .whereLayer("Config").mayNotBeAccessedByAnyLayer()
            .whereLayer("Adapters").mayOnlyBeAccessedByLayers("Config")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapters", "Config")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapters", "Config");

    @ArchIgnore(reason = "active a l'etape 5 (adaptateur REST)")
    @ArchTest
    static final ArchRule inbound_adapters_do_not_use_outbound_adapters = noClasses()
            .that().resideInAPackage(ROOT + ".adapters.in..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".adapters.out..")
            .because("un adaptateur entrant passe par les ports in, jamais par une implementation sortante");

    @ArchTest
    static final ArchRule outbound_adapters_do_not_use_inbound_adapters = noClasses()
            .that().resideInAPackage(ROOT + ".adapters.out..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".adapters.in..");

    @ArchTest
    static final ArchRule outbound_adapters_are_independent = slices()
            .matching(ROOT + ".adapters.out.(*)..")
            .should().notDependOnEachOther()
            .because("Confluence, Jira et LLM sont interchangeables independamment");

    @ArchTest
    static final ArchRule outbound_ports_are_interfaces = classes()
            .that().resideInAPackage(ROOT + ".application.port.out..")
            .should().beInterfaces();

    @ArchIgnore(reason = "active a l'etape 4 (services applicatifs)")
    @ArchTest
    static final ArchRule no_jira_identifier_in_domain_and_application = classes()
            .that().resideInAnyPackage(DOMAIN, APPLICATION)
            .should(haveNoIdentifierContaining("jira"))
            .because("le centre reste agnostique du gestionnaire de tickets (OpenProject en v1.1)");

    /** Nom de classe, de champ ou de methode (insensible a la casse : jira, Jira, JIRA). */
    private static ArchCondition<JavaClass> haveNoIdentifierContaining(String word) {
        return new ArchCondition<>("have no class, field or method name containing '" + word + "'") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (contains(javaClass.getSimpleName(), word)) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                            "class " + javaClass.getName() + " is named with '" + word + "'"));
                }
                Stream.<JavaMember>concat(javaClass.getFields().stream(), javaClass.getMethods().stream())
                        .filter(member -> contains(member.getName(), word))
                        .forEach(member -> events.add(SimpleConditionEvent.violated(member,
                                member.getFullName() + " is named with '" + word + "'")));
            }
        };
    }

    private static boolean contains(String identifier, String word) {
        return identifier.toLowerCase(Locale.ROOT).contains(word);
    }
}
