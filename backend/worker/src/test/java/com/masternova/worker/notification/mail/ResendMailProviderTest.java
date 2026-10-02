package com.masternova.worker.notification.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.masternova.worker.notification.NotificationProperties;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** The Resend adapter against a mocked HTTP server: request shape and error translation. */
class ResendMailProviderTest {

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final ResendMailProvider provider =
      new ResendMailProvider(
          builder,
          new NotificationProperties(
              URI.create("http://localhost:4200"),
              "Masternova <no-reply@masternova.dev>",
              NotificationProperties.Provider.RESEND,
              "test-unsubscribe-secret-0123456789abcdef",
              new NotificationProperties.Resend(URI.create("https://api.resend.test"), "re_key")));

  private final OutboundEmail email =
      new OutboundEmail(
          "Masternova <no-reply@masternova.dev>",
          "asha@example.com",
          "Hello",
          "<p>Hi</p>",
          "Hi",
          Map.of("List-Unsubscribe", "<https://x/u>"));

  @Test
  void adaptsOurEmailToResendsJsonApi() {
    server
        .expect(requestTo("https://api.resend.test/emails"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer re_key"))
        .andExpect(
            content()
                .json(
                    """
                    {"from":"Masternova <no-reply@masternova.dev>","to":["asha@example.com"],
                     "subject":"Hello","html":"<p>Hi</p>","text":"Hi",
                     "headers":{"List-Unsubscribe":"<https://x/u>"}}
                    """))
        .andRespond(withSuccess("{\"id\":\"re_123\"}", MediaType.APPLICATION_JSON));

    assertThat(provider.send(email)).isEqualTo("re_123");
    server.verify();
  }

  @Test
  void a422IsAPermanentRejection() {
    server
        .expect(requestTo("https://api.resend.test/emails"))
        .andRespond(
            withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .body("{\"name\":\"validation_error\",\"message\":\"Invalid `to` field\"}")
                .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> provider.send(email))
        .isInstanceOf(PermanentDeliveryException.class)
        .hasMessageContaining("Invalid `to` field");
  }

  @Test
  void rateLimitsAndServerErrorsAreTemporary() {
    server
        .expect(requestTo("https://api.resend.test/emails"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    assertThatThrownBy(() -> provider.send(email))
        .isInstanceOf(RestClientResponseException.class)
        .isNotInstanceOf(PermanentDeliveryException.class);
  }

  @Test
  void aMissingApiKeyFailsAtStartupNotAtTheFirstEmail() {
    assertThatThrownBy(
            () ->
                new ResendMailProvider(
                    RestClient.builder(),
                    new NotificationProperties(
                        URI.create("http://localhost:4200"),
                        "x@y.z",
                        NotificationProperties.Provider.RESEND,
                        "test-unsubscribe-secret-0123456789abcdef",
                        new NotificationProperties.Resend(URI.create("https://x"), " "))))
        .hasMessageContaining("api-key is required");
  }
}
