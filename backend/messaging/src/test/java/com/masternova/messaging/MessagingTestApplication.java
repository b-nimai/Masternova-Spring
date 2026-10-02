package com.masternova.messaging;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The smallest Boot app for this library's tests: DataSource, JdbcClient, Flyway (this module's own
 * V2 migration), transactions and Jackson come from Boot; the outbox beans from {@code
 * MessagingAutoConfiguration} — exactly as in the api and the worker.
 */
@SpringBootApplication
class MessagingTestApplication {}
