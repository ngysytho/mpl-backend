package com.example.userservice.config;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
@Configuration(proxyBeanMethods = false)
public class DatabaseConfig {
    /** Fail before datasource beans initialize if a deployment targets an unapproved database. */
    @Bean static BeanFactoryPostProcessor applicationDatabaseGuard(Environment env) {
        return factory -> {
            String database = env.getProperty("app.database-name", "user_service_db");
            if (!"user_service_db".equals(database)) throw new IllegalStateException("Application database must be user_service_db");
            for (String property : new String[]{"spring.datasource.url", "spring.liquibase.url"}) {
                String url = env.getProperty(property);
                if (url != null && !url.matches("jdbc:postgresql://[^/]+/user_service_db(?:[?].*)?"))
                    throw new IllegalStateException("Refusing non-application database URL for " + property);
            }
            if (env.getProperty("spring.liquibase.enabled", Boolean.class, false))
                throw new IllegalStateException("Runtime migrations are forbidden; run mpl-db-migrations first");
            if (!"validate".equals(env.getProperty("spring.jpa.hibernate.ddl-auto", "validate")))
                throw new IllegalStateException("Hibernate must only validate the application schema");
        };
    }
}
