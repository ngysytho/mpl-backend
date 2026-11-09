package com.example.userservice.integration;

import org.springframework.context.*;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.*;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import java.time.Duration;
import java.nio.file.Path;

/** Creates only disposable resources. Migrations come from the independently built image. */
public class TestDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    static final String MIGRATOR_PASSWORD="disposable-migrator";
    static final String RUNTIME_PASSWORD="disposable-runtime";
    static Network network;
    static PostgreSQLContainer postgres;
    public void initialize(ConfigurableApplicationContext context) {
        network=Network.newNetwork();
        postgres=new PostgreSQLContainer("postgres:17.6-bookworm").withDatabaseName("user_service_db")
            .withUsername("postgres").withPassword("disposable-bootstrap").withNetwork(network).withNetworkAliases("app-db")
            .withEnv("APP_MIGRATION_PASSWORD",MIGRATOR_PASSWORD).withEnv("APP_RUNTIME_PASSWORD",RUNTIME_PASSWORD)
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("docker/init-app-db.sh").toAbsolutePath()),"/docker-entrypoint-initdb.d/10-init.sh");
        try {
            postgres.start(); migrate("update");
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "spring.datasource.url="+postgres.getJdbcUrl(),"spring.datasource.username=app_runtime",
                "spring.datasource.password="+RUNTIME_PASSWORD,"spring.liquibase.enabled=false");
            context.addApplicationListener(event -> { if(event instanceof ContextClosedEvent) close(); });
        } catch(RuntimeException failure) { close();throw failure; }
    }
    static void migrate(String command) {
        try(var migration=new GenericContainer<>(System.getProperty("migration.image","mpl-db-migrations:1.0.0"))
            .withNetwork(network).withEnv("DB_URL","jdbc:postgresql://app-db:5432/user_service_db")
            .withEnv("DB_MIGRATION_USERNAME","app_migrator").withEnv("DB_MIGRATION_PASSWORD",MIGRATOR_PASSWORD)
            .withCommand(command).withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(2)))) {
            migration.start();
        }
    }
    static void close() { if(postgres!=null)postgres.stop();if(network!=null)network.close(); }
}
