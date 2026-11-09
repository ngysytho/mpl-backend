package com.example.userservice.integration;
import com.example.userservice.core.exception.ServiceException;
import com.example.userservice.modules.user.dto.request.*;
import com.example.userservice.modules.user.service.UserQuery;
import com.example.userservice.modules.user.service.*;
import com.example.userservice.modules.user.entity.*;
import com.example.userservice.modules.user.repository.UserRepository;
import com.example.userservice.modules.user.repository.ProvisioningAdapter;
import org.junit.jupiter.api.*;
import com.example.userservice.UserServiceApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;


import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@org.springframework.boot.test.context.SpringBootTest(classes={UserServiceApplication.class,UserPersistenceIT.TestOverrides.class}, webEnvironment=org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.test.context.ContextConfiguration(initializers=TestDatabaseInitializer.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class UserPersistenceIT {
    @org.springframework.beans.factory.annotation.Autowired UserRepository users;
    @org.springframework.beans.factory.annotation.Autowired UserService service;
    @org.springframework.beans.factory.annotation.Autowired ProvisioningPort provisioning;
    @org.springframework.beans.factory.annotation.Autowired IdentitySyncPort sync;
    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired PlatformTransactionManager transactionManager;
    @org.springframework.beans.factory.annotation.Autowired IdentityManagementPort identities;
    @org.springframework.beans.factory.annotation.Autowired UserTransactions transactions;
    @org.springframework.beans.factory.annotation.Autowired io.micrometer.core.instrument.MeterRegistry metrics;
    @TestConfiguration(proxyBeanMethods=false)
    static class TestOverrides {
        @Bean @Primary IdentityManagementPort testIdentities() { return mock(IdentityManagementPort.class); }
        @Bean @Primary JwtDecoder testDecoder() { return mock(JwtDecoder.class); }
    }
    JdbcTemplate ddl;
    @BeforeEach void clear() {
        reset(identities);
        // Fault-injection DDL is restricted to this Testcontainers-created database's migrator.
        var migrationSource=new org.springframework.jdbc.datasource.DriverManagerDataSource(
            TestDatabaseInitializer.postgres.getJdbcUrl(),"app_migrator",TestDatabaseInitializer.MIGRATOR_PASSWORD);
        ddl=new JdbcTemplate(migrationSource);
        ddl.execute("DROP TRIGGER IF EXISTS test_reject_insert ON users");
        ddl.execute("DROP FUNCTION IF EXISTS test_reject_user_insert()");
        jdbc.update("DELETE FROM user_registration_rate_limits"); jdbc.update("DELETE FROM user_identity_sync"); jdbc.update("DELETE FROM user_provisioning_requests"); jdbc.update("DELETE FROM users");
        when(identities.findByExactUsername(anyString())).thenReturn(Optional.empty()); when(identities.findByExactEmail(anyString())).thenReturn(Optional.empty());
        when(identities.createDisabled(anyString(),anyString(),any())).thenReturn("new-kc-id");
    }
    private User profile(String name, String email) {
        return new User(UUID.randomUUID(),UUID.randomUUID().toString(),name,email,null,null,UserStatus.ACTIVE,Instant.parse("2026-01-01T00:00:00Z"),Instant.parse("2026-01-01T00:00:00Z"));
    }
    private CreateUserCommand command() { return new CreateUserCommand("Nguyen Van A","a@example.com",null,null); }
    @Test void liquibaseMigratesApplicationDatabaseAndHibernateValidates() {
        assertThat(jdbc.queryForObject("select current_database()",String.class)).isEqualTo("user_service_db");
        TestDatabaseInitializer.migrate("update");
        assertThat(jdbc.queryForObject("select count(*) from databasechangelog",Long.class)).isEqualTo(5L);
        assertThat(jdbc.queryForObject("select count(*) from databasechangeloglock where locked=false",Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from users",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_name='credential'",Long.class)).isZero();
    }
    @Test void repositoryRoundTripsAndFiltersInDatabase() {
        var a=users.save(profile("Nguyen Alpha","alpha@example.com")); users.save(profile("Other Name","beta@example.com")); users.save(profile("Nguyen Gamma","gamma@example.com"));
        assertThat(users.findById(a.id())).contains(a);
        var page=users.search(UserQuery.filter("nguyen",null,null,UserStatus.ACTIVE,Instant.parse("2025-01-01T00:00:00Z"),null,0,1,"email,asc"));
        assertThat(page.totalElements()).isEqualTo(2); assertThat(page.totalPages()).isEqualTo(2); assertThat(page.content()).containsExactly(a);
        assertThat(users.search(UserQuery.filter("%",null,null,null,null,null,0,20,"email,asc")).totalElements()).isZero();
        assertThat(users.search(UserQuery.filter(null,"Other","beta",null,null,null,0,20,"fullName,asc")).totalElements()).isEqualTo(1);
    }
    @Test void databaseRejectsDuplicateEmailAndIdentityAndNonNormalizedEmail() {
        var a=users.save(profile("A","a@example.com"));
        assertThatThrownBy(() -> users.save(profile("B","a@example.com"))).isInstanceOf(DataIntegrityViolationException.class);
        var b=profile("B","b@example.com");
        assertThatThrownBy(() -> users.save(new User(b.id(),a.keycloakUserId(),b.fullName(),b.email(),null,null,b.status(),b.createdAt(),b.updatedAt())))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET email='UPPER@example.com' WHERE id=?",a.id())).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void repositoryParticipatesInOuterTransaction() {
        UUID id=UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            var u=profile("Name","rollback@example.com");
            users.save(new User(id,u.keycloakUserId(),u.fullName(),u.email(),null,null,u.status(),u.createdAt(),u.updatedAt()));
            sync.enqueue(id); tx.setRollbackOnly();
        });
        assertThat(users.findById(id)).isEmpty(); assertThat(jdbc.queryForObject("select count(*) from user_identity_sync",Long.class)).isZero();
    }
    @Test void identicalRetryCreatesOnlyOneIdentityAndOneProfile() {
        var key=UUID.randomUUID(); var first=service.create(key,command()); var replay=service.create(key,command());
        assertThat(replay.id()).isEqualTo(first.id()); verify(identities,times(1)).createDisabled(anyString(),anyString(),eq(key));
        assertThat(jdbc.queryForObject("select count(*) from users",Long.class)).isEqualTo(1L);
        verify(identities).assignRealmRole("new-kc-id","USER"); verify(identities).setEnabled("new-kc-id",true);
    }
    @Test void rejectsReuseOfKeyWithDifferentPayloadAndEmailWithDifferentKey() {
        var key=UUID.randomUUID(); service.create(key,command());
        assertThatThrownBy(() -> service.create(key,new CreateUserCommand("Different","a@example.com",null,null))).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.create(UUID.randomUUID(),command())).isInstanceOf(DataIntegrityViolationException.class);
        verify(identities,times(1)).createDisabled(anyString(),anyString(),any());
    }
    @Test void committedJournalRecoversAfterDatabaseFailureFollowingIdentityCreation() {
        var key=UUID.randomUUID();
        var owned=new IdentityManagementPort.Identity("new-kc-id","a@example.com","a@example.com","Name",false,Map.of(ProvisioningAdapter.OWNER_ATTRIBUTE,List.of(key.toString())));
        when(identities.findByExactUsername("a@example.com")).thenReturn(Optional.empty(),Optional.of(owned));
        ddl.execute("CREATE FUNCTION test_reject_user_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'simulated persistence failure'; END $$");
        ddl.execute("CREATE TRIGGER test_reject_insert BEFORE INSERT ON users FOR EACH ROW EXECUTE FUNCTION test_reject_user_insert()");
        assertThatThrownBy(() -> service.create(key,command())).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_provisioning_requests WHERE user_id IS NULL",Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users",Long.class)).isZero();
        verify(identities,never()).setEnabled(anyString(),eq(true)); verify(identities,never()).deleteOwnedIdentity(anyString(),any());
        ddl.execute("DROP TRIGGER test_reject_insert ON users");
        var recovered=service.create(key,command()); assertThat(recovered.keycloakUserId()).isEqualTo("new-kc-id");
        verify(identities,times(1)).createDisabled(anyString(),anyString(),any());
    }
    @Test void doesNotAdoptOrDeletePreExistingIdentity() {
        when(identities.findByExactUsername(anyString())).thenReturn(Optional.of(new IdentityManagementPort.Identity("unrelated","a@example.com","a@example.com","Name",true,Map.of())));
        assertThatThrownBy(() -> service.create(UUID.randomUUID(),command())).isInstanceOf(ServiceException.class);
        verify(identities,never()).createDisabled(anyString(),anyString(),any()); verify(identities,never()).deleteOwnedIdentity(anyString(),any());
    }
    @Test void failedDisableRemainsDurableAndCanBeRetried() {
        var user=users.save(profile("Name","a@example.com"));
        doThrow(new RuntimeException("offline")).when(identities).setEnabled(user.keycloakUserId(),false);
        assertThatThrownBy(() -> service.status(user.id(),UserStatus.SUSPENDED)).isInstanceOf(ServiceException.class);
        assertThat(users.findById(user.id()).orElseThrow().status()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_identity_sync",Long.class)).isEqualTo(1L);
        doNothing().when(identities).setEnabled(user.keycloakUserId(),false);
        assertThat(sync.synchronize(user.id())).isTrue(); assertThat(jdbc.queryForObject("SELECT count(*) FROM user_identity_sync",Long.class)).isZero();
    }
    @Test void concurrentRetriesAreSerializedByTheJournal() throws Exception {
        var key=UUID.randomUUID();
        try (var executor=Executors.newFixedThreadPool(2)) {
            var one=executor.submit(() -> service.create(key,command())); var two=executor.submit(() -> service.create(key,command()));
            assertThat(one.get(30,TimeUnit.SECONDS).id()).isEqualTo(two.get(30,TimeUnit.SECONDS).id());
        }
        verify(identities,times(1)).createDisabled(anyString(),anyString(),any());
    }

    @Test void registrationAndAdminIdempotencyKeysAreSeparated() {
        UUID key=UUID.randomUUID();
        provisioning.reserve(key,command());
        var registration=new RegisterUserCommand(command(),"Example-Password-42!");
        assertThatThrownBy(() -> provisioning.reserveRegistration(key,registration)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> provisioning.provisionRegistration(key,registration)).isInstanceOf(ServiceException.class);
        verify(identities,never()).createDisabledWithPassword(anyString(),anyString(),any(),anyString());
    }
    @Test void registrationReplayDoesNotResetPasswordOrCreateAnotherIdentity() {
        UUID key=UUID.randomUUID();var registration=new RegisterUserCommand(command(),"Example-Password-42!");
        when(identities.createDisabledWithPassword(anyString(),anyString(),any(),anyString())).thenReturn("registered-id");
        provisioning.reserveRegistration(key,registration);var first=provisioning.provisionRegistration(key,registration);
        var retry=new RegisterUserCommand(command(),"Another-Password-42!");
        provisioning.reserveRegistration(key,retry);var second=provisioning.provisionRegistration(key,retry);
        assertThat(second.id()).isEqualTo(first.id());
        verify(identities,times(1)).createDisabledWithPassword("a@example.com","Nguyen Van A",key,"Example-Password-42!");
        assertThat(jdbc.queryForObject("SELECT request_kind FROM user_provisioning_requests WHERE id=?",String.class,key)).isEqualTo("SELF_REGISTRATION");
    }
    @Test void registrationRecoveryAdoptsOnlyTheOwnedIdentityWithoutChangingItsCredential() {
        UUID key=UUID.randomUUID();var registration=new RegisterUserCommand(command(),"Example-Password-42!");
        var owned=new IdentityManagementPort.Identity("registered-id","a@example.com","a@example.com","Name",false,
            Map.of(ProvisioningAdapter.OWNER_ATTRIBUTE,List.of(key.toString())));
        when(identities.createDisabledWithPassword(anyString(),anyString(),any(),anyString())).thenReturn("registered-id");
        when(identities.findByExactUsername("a@example.com")).thenReturn(Optional.empty(),Optional.of(owned));
        provisioning.reserveRegistration(key,registration);
        ddl.execute("CREATE FUNCTION test_reject_user_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'simulated persistence failure'; END $$");
        ddl.execute("CREATE TRIGGER test_reject_insert BEFORE INSERT ON users FOR EACH ROW EXECUTE FUNCTION test_reject_user_insert()");
        assertThatThrownBy(() -> provisioning.provisionRegistration(key,registration)).isInstanceOf(RuntimeException.class);
        ddl.execute("DROP TRIGGER test_reject_insert ON users");
        var recovered=provisioning.provisionRegistration(key,new RegisterUserCommand(command(),"Another-Password-42!"));
        assertThat(recovered.keycloakUserId()).isEqualTo("registered-id");
        verify(identities,times(1)).createDisabledWithPassword(anyString(),anyString(),any(),anyString());
        verify(identities,never()).deleteOwnedIdentity(anyString(),any());
    }
    @Test void registrationQuotaPersistsAcrossLimiterInstances() {
        var one=new com.example.userservice.modules.user.repository.RegistrationRateLimiter(jdbc,2);
        var two=new com.example.userservice.modules.user.repository.RegistrationRateLimiter(jdbc,2);
        one.check("192.0.2.1");two.check("192.0.2.1");
        assertThatThrownBy(() -> one.check("192.0.2.1")).isInstanceOfSatisfying(ServiceException.class,e -> assertThat(e.status()).isEqualTo(429));
        one.check("192.0.2.2");
    }

    @Test void ambiguousCreateTimeoutRecoversOwnedIdentityWithOriginalKey() {
        var key=UUID.randomUUID();
        var owned=new IdentityManagementPort.Identity("new-kc-id","a@example.com","a@example.com","Name",false,
            Map.of(ProvisioningAdapter.OWNER_ATTRIBUTE,List.of(key.toString())));
        when(identities.findByExactUsername("a@example.com")).thenReturn(Optional.empty(),Optional.of(owned));
        when(identities.createDisabled(anyString(),anyString(),eq(key))).thenThrow(com.example.userservice.integration.keycloak.IdentityException.unavailable());
        assertThatThrownBy(()->service.create(key,command())).isInstanceOf(ServiceException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_provisioning_requests WHERE user_id IS NULL",Long.class)).isEqualTo(1);
        assertThat(service.create(key,command()).keycloakUserId()).isEqualTo("new-kc-id");
        verify(identities,times(1)).createDisabled(anyString(),anyString(),eq(key));
        verify(identities,never()).deleteOwnedIdentity(anyString(),any());
    }
    @Test void simultaneousDifferentKeysForSameEmailNeverCreateTwoIdentities() throws Exception {
        var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> attempt=()->{gate.await();try {service.create(UUID.randomUUID(),command());return true;}
                catch(DataIntegrityViolationException expected){return false;}};
            var one=executor.submit(attempt);var two=executor.submit(attempt);gate.countDown();
            assertThat(List.of(one.get(30,TimeUnit.SECONDS),two.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        verify(identities,times(1)).createDisabled(anyString(),anyString(),any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users",Long.class)).isEqualTo(1);
    }
    @Test void parallelWorkersAndRepeatedDeliveryDoNotRepeatCompletedSideEffects() throws Exception {
        var user=users.save(profile("Name","a@example.com"));
        new TransactionTemplate(transactionManager).executeWithoutResult(tx->sync.enqueue(user.id()));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("test timeout");return null;})
            .when(identities).updateProfile(user.keycloakUserId(),user.fullName());
        try(var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(()->sync.synchronize(user.id()));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            var second=executor.submit(()->sync.synchronize(user.id()));
            try {
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(()->
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'",Long.class)).isPositive());
            } finally { release.countDown(); }
            assertThat(first.get(15,TimeUnit.SECONDS)).isTrue();assertThat(second.get(15,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(sync.synchronize(user.id())).isTrue();
        verify(identities,times(1)).updateProfile(user.keycloakUserId(),user.fullName());
        verify(identities,times(1)).assignRealmRole(user.keycloakUserId(),"USER");
        verify(identities,times(1)).setEnabled(user.keycloakUserId(),true);
        var lock=metrics.get("user.identity.sync.lock.wait").timer();
        var delivery=metrics.get("user.identity.sync.delivery.duration").timer();
        assertThat(lock.count()).isPositive();assertThat(delivery.count()).isPositive();
        System.out.printf("Controlled concurrency timers: lock max=%.3f ms, delivery max=%.3f ms%n",
            lock.max(TimeUnit.MILLISECONDS),delivery.max(TimeUnit.MILLISECONDS));
    }
    @Test void olderPendingActivationReadsNewestSuspendedStateAndNeverEnables() {
        var user=users.save(profile("Name","a@example.com"));
        new TransactionTemplate(transactionManager).executeWithoutResult(tx->sync.enqueue(user.id()));
        transactions.status(user.id(),UserStatus.SUSPENDED);
        assertThat(sync.synchronize(user.id())).isTrue();assertThat(sync.synchronize(user.id())).isTrue();
        var order=inOrder(identities);order.verify(identities).setEnabled(user.keycloakUserId(),false);
        order.verify(identities).updateProfile(user.keycloakUserId(),user.fullName());
        verify(identities,never()).setEnabled(anyString(),eq(true));
    }
    @Test void outageRetainsOutboxWithBackoffAndRecoveryIsIdempotent() {
        var user=users.save(profile("Name","a@example.com"));
        new TransactionTemplate(transactionManager).executeWithoutResult(tx->sync.enqueue(user.id()));
        doThrow(com.example.userservice.integration.keycloak.IdentityException.unavailable()).when(identities).updateProfile(anyString(),anyString());
        assertThat(sync.synchronize(user.id())).isFalse();
        assertThat(jdbc.queryForObject("SELECT attempts FROM user_identity_sync WHERE user_id=?",Integer.class,user.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT available_at>now() FROM user_identity_sync WHERE user_id=?",Boolean.class,user.id())).isTrue();
        verify(identities,never()).setEnabled(anyString(),eq(true));
        doNothing().when(identities).updateProfile(anyString(),anyString());
        assertThat(sync.synchronize(user.id())).isTrue();assertThat(sync.synchronize(user.id())).isTrue();
        verify(identities,times(1)).setEnabled(user.keycloakUserId(),true);
    }

    @Test void suspensionWaitingBehindInFlightActivationWinsFinalState() throws Exception {
        var user=users.save(profile("Name","a@example.com"));
        new TransactionTemplate(transactionManager).executeWithoutResult(tx->sync.enqueue(user.id()));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("test timeout");return null;})
            .when(identities).updateProfile(user.keycloakUserId(),user.fullName());
        try(var executor=Executors.newFixedThreadPool(2)) {
            var activation=executor.submit(()->sync.synchronize(user.id()));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            var suspension=executor.submit(()->service.status(user.id(),UserStatus.SUSPENDED));
            try {
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(()->
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'",Long.class)).isPositive());
            } finally {release.countDown();}
            assertThat(activation.get(15,TimeUnit.SECONDS)).isTrue();
            assertThat(suspension.get(15,TimeUnit.SECONDS).status()).isEqualTo(UserStatus.SUSPENDED);
        }
        var order=inOrder(identities);order.verify(identities).setEnabled(user.keycloakUserId(),true);
        order.verify(identities).setEnabled(user.keycloakUserId(),false);
        assertThat(sync.synchronize(user.id())).isTrue();
        verify(identities,times(1)).setEnabled(user.keycloakUserId(),true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_identity_sync",Long.class)).isZero();
    }
}
