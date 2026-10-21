package com.masternova.api.platform.events;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tells Modulith's JDBC event registry (ADR-0021) that the database is Postgres instead of letting
 * it ask. ⭐ Its {@code databaseType} bean opens a connection to read the metadata while the context
 * starts — a plain {@code @Bean}, no property, no override — and the image's AOT training run
 * starts the app with no database (backend/Dockerfile), so the build failed there. Postgres is the
 * only database this app runs on; the definition is replaced before any bean is made.
 *
 * <p>The type is package-private in Modulith, so it is named by string: a Modulith upgrade that
 * renames it fails every start (and every IT) loudly, never silently.
 */
@Configuration(proxyBeanMethods = false)
class EventRegistryDatabaseConfig {

  static final String BEAN = "databaseType";
  static final String TYPE = "org.springframework.modulith.events.jdbc.DatabaseType";

  @Bean
  static BeanDefinitionRegistryPostProcessor eventRegistryOnPostgres() {
    return new BeanDefinitionRegistryPostProcessor() {
      @Override
      public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        if (!registry.containsBeanDefinition(BEAN)) {
          throw new IllegalStateException("Modulith's '" + BEAN + "' bean is gone: see " + TYPE);
        }
        registry.removeBeanDefinition(BEAN);
        registry.registerBeanDefinition(BEAN, postgres(type()));
      }

      @Override
      public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {}
    };
  }

  private static Class<?> type() {
    try {
      return Class.forName(TYPE, false, EventRegistryDatabaseConfig.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Modulith's database type moved: " + TYPE, e);
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static RootBeanDefinition postgres(Class type) {
    return new RootBeanDefinition(type, () -> Enum.valueOf(type, "POSTGRES"));
  }
}
