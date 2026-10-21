package com.masternova.api.catalog.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.TestInstructors;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Keyword search over the generated tsvector (V27) and the trigram typeahead (V8). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class KeywordSearchIT {

  @Autowired MockMvcTester mvc;
  @Autowired DataSource dataSource;
  SearchFixtures courses;

  @BeforeEach
  void setUp() {
    courses =
        new SearchFixtures(dataSource, new TestInstructors(dataSource).create("Asha Rao").id());
  }

  @AfterEach
  void clean() {
    new TestInstructors(dataSource).deleteAll();
  }

  @Test
  void englishIsStemmedAndATitleHitOutranksADescriptionHit() {
    UUID inDescription =
        courses.published("Cloud basics", null, "We deploy a small app to a container platform.");
    UUID inTitle = courses.published("Deploy with Docker", "Containers from zero", "Hands on.");
    courses.published("Watercolour", null, "Painting for beginners.");

    List<String> ids = ids(search("deploying containers", "KEYWORD"));

    assertThat(ids).containsExactly(inTitle.toString(), inDescription.toString());
  }

  @Test
  void otherLanguagesMatchWithoutStemming() {
    UUID hindi = courses.course("पायथन सीखें", null, "डेटा विज्ञान की शुरुआत", "hi", "PUBLISHED");

    assertThat(ids(search("पायथन", "KEYWORD"))).containsExactly(hindi.toString());
  }

  @Test
  void onlyPublishedCoursesAreFound() {
    courses.course("Kubernetes draft", null, "Not yet.", "en", "DRAFT");
    UUID live = courses.published("Kubernetes live", null, "Published.");

    assertThat(ids(search("kubernetes", "KEYWORD"))).containsExactly(live.toString());
  }

  @Test
  void anEditIsSearchableAtOnce() {
    UUID course = courses.published("Networking", null, "Routers and switches.");
    assertThat(ids(search("firewall", "KEYWORD"))).isEmpty();

    courses.edit(course, "Routers, switches and the firewall."); // the generated column follows

    assertThat(ids(search("firewall", "KEYWORD"))).containsExactly(course.toString());
  }

  @Test
  void withoutAnEmbeddingModelSemanticAsksAnswerFromKeywords() {
    courses.published("Kubernetes in practice", null, "Pods.");

    JsonNode body = search("kubernetes", "HYBRID");

    assertThat(body.get("mode").asString()).isEqualTo("KEYWORD");
    assertThat(body.get("degraded").asBoolean()).isTrue();
    assertThat(body.get("items")).hasSize(1);
  }

  @Test
  void suggestionsForgiveAPrefixAndATypo() {
    courses.published("Kubernetes in Practice", null, "Pods.");
    courses.published("Painting with Watercolour", null, "Brushes.");

    assertThat(titles(suggest("kube"))).containsExactly("Kubernetes in Practice");
    assertThat(titles(suggest("kubernets"))).containsExactly("Kubernetes in Practice");
    assertThat(mvc.get().uri("/api/v1/search/suggestions?q=k")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(mvc.get().uri("/api/v1/search/courses?q=")).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void stopWordsAloneFindNothingAndBreakNothing() {
    courses.published("The art of the deal", null, "A book.");

    assertThat(ids(search("the", "KEYWORD"))).isEmpty();
    assertThat(ids(search("\"unbalanced -(or", "KEYWORD"))).isEmpty(); // websearch never throws
  }

  // --- helpers ---

  private JsonNode search(String q, String mode) {
    return json(mvc.get().uri("/api/v1/search/courses?q={q}&mode={m}", q, mode).exchange());
  }

  private JsonNode suggest(String q) {
    return json(mvc.get().uri("/api/v1/search/suggestions?q={q}", q).exchange());
  }

  private static JsonNode json(MvcTestResult result) {
    assertThat(result).hasStatusOk();
    try {
      return JsonMapper.shared()
          .readTree(
              result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    } catch (java.io.UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static List<String> ids(JsonNode body) {
    List<String> ids = new ArrayList<>();
    body.get("items").forEach(i -> ids.add(i.get("id").asString()));
    return ids;
  }

  private static List<String> titles(JsonNode body) {
    List<String> titles = new ArrayList<>();
    body.forEach(i -> titles.add(i.get("title").asString()));
    return titles;
  }
}
