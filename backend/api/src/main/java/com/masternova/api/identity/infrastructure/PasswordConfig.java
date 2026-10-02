package com.masternova.api.identity.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
class PasswordConfig {

  /**
   * ⭐ Spring's DelegatingPasswordEncoder — the STRATEGY pattern: hashes are stored as
   * "{bcrypt}$2a$10$…", the {id} prefix picks the algorithm. New passwords use today's default
   * (bcrypt); old hashes keep verifying even after the default changes (e.g. to argon2).
   */
  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }
}
