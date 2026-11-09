package com.example.userservice.config;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import static org.assertj.core.api.Assertions.*;
class DatabaseConfigTest {
    @Test void rejectsRuntimeMigrationOverrideWrongDatabaseAndHibernateDdl() {
        for(var env:java.util.List.of(
            new MockEnvironment().withProperty("spring.liquibase.enabled","true"),
            new MockEnvironment().withProperty("spring.datasource.url","jdbc:postgresql://localhost:5432/keycloak_db"),
            new MockEnvironment().withProperty("spring.jpa.hibernate.ddl-auto","update")))
            assertThatThrownBy(()->DatabaseConfig.applicationDatabaseGuard(env).postProcessBeanFactory(new DefaultListableBeanFactory()))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void allowsRuntimeDmlTargetWithSchemaValidation() {
        var env=new MockEnvironment().withProperty("spring.datasource.url","jdbc:postgresql://localhost:5432/user_service_db")
            .withProperty("spring.liquibase.enabled","false").withProperty("spring.jpa.hibernate.ddl-auto","validate");
        DatabaseConfig.applicationDatabaseGuard(env).postProcessBeanFactory(new DefaultListableBeanFactory());
    }
}
