package com.example.userservice;
import com.example.userservice.modules.user.entity.User;
import com.example.userservice.modules.user.entity.UserStatus;
import com.tngtech.archunit.junit.*;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
@AnalyzeClasses(packages = "com.example.userservice", importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    // The package-only reorganization retains the existing pure model beside the JPA entity.
    @ArchTest static final ArchRule userModelIsIndependent = noClasses().that().haveFullyQualifiedName(User.class.getName())
        .or().haveFullyQualifiedName(UserStatus.class.getName())
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..",
            "org.keycloak..", "..integration..", "..modules.user.service..", "..modules.user.controller..", "..modules.user.dto..");
    @ArchTest static final ArchRule servicesUseExistingContracts = noClasses().that().resideInAPackage("..modules.user.service..")
        .should().dependOnClassesThat().resideInAnyPackage("..integration..", "jakarta.persistence..", "org.springframework.data..",
            "..modules.user.controller..", "..modules.user.dto.response..");
}
