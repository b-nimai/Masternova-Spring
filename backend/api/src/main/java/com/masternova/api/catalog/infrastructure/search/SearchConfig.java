package com.masternova.api.catalog.infrastructure.search;

import com.masternova.api.catalog.domain.search.HybridRanker;
import io.micrometer.observation.ObservationRegistry;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.RestClient;

/**
 * Semantic search, wired in ONE visible place and only when {@code
 * masternova.search.embedding.enabled=true} (ADR-0024 §6). Without it the context holds the keyword
 * ranker alone, and semantic or hybrid requests answer from keywords, {@code degraded}.
 *
 * <p>Spring AI is used for the model client only, built here rather than by its starter (which
 * would also bring chat clients and their auto-configuration).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SearchProperties.class)
@ConditionalOnProperty(name = "masternova.search.embedding.enabled", havingValue = "true")
class SearchConfig {

  /**
   * The Ollama client. Its HTTP read timeout is generous (a batch of 32 texts on a CPU takes
   * seconds); the QUERY path has its own 2 s budget in {@link QueryEmbedder}.
   */
  @Bean
  EmbeddingModel embeddingModel(SearchProperties props, ObservationRegistry observations) {
    var http =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
    http.setReadTimeout(Duration.ofSeconds(60));
    OllamaApi api =
        OllamaApi.builder()
            .baseUrl(props.baseUrl())
            .restClientBuilder(RestClient.builder().requestFactory(http))
            .build();
    return OllamaEmbeddingModel.builder()
        .ollamaApi(api)
        .options(OllamaEmbeddingOptions.builder().model(props.model()).build())
        .modelManagementOptions(ModelManagementOptions.defaults()) // never pulls: `make ai` does
        .observationRegistry(observations)
        .build();
  }

  @Bean(destroyMethod = "close")
  QueryEmbedder queryEmbedder(EmbeddingModel model, SearchProperties props, Clock clock) {
    return new QueryEmbedder(model, props.queryTimeout(), props.breakerOpenFor(), clock);
  }

  @Bean
  SemanticRanker semanticRanker(JdbcClient jdbc, QueryEmbedder embedder, SearchProperties props) {
    return new SemanticRanker(jdbc, embedder, props.maxDistance());
  }

  @Bean
  HybridRanker hybridRanker(KeywordRanker keyword, SemanticRanker semantic) {
    return new HybridRanker(keyword, semantic);
  }

  @Bean
  CourseEmbeddingIndexer courseEmbeddingIndexer(
      JdbcClient jdbc,
      EmbeddingModel model,
      TransactionOperations tx,
      SearchProperties props,
      Clock clock) {
    return new CourseEmbeddingIndexer(jdbc, model, tx, props.model(), props.batchSize(), clock);
  }

  @Bean
  @ConditionalOnProperty(
      name = "masternova.search.embedding.index-enabled",
      havingValue = "true",
      matchIfMissing = true)
  CourseEmbeddingJob courseEmbeddingJob(CourseEmbeddingIndexer indexer) {
    return new CourseEmbeddingJob(indexer);
  }
}
