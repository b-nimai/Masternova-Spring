package com.masternova.worker.notification.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;

/**
 * The SMTP adapter against a real SMTP server: Mailpit in a container, read back through Mailpit's
 * REST API. Proves the email arrives as multipart/alternative with BOTH parts and our headers.
 */
@Testcontainers
class SmtpMailProviderIT {

  @Container
  static final GenericContainer<?> mailpit =
      new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
          .withExposedPorts(1025, 8025);

  @Test
  void anEmailArrivesWithHtmlTextAndHeaders() {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(mailpit.getHost());
    sender.setPort(mailpit.getMappedPort(1025));
    SmtpMailProvider provider = new SmtpMailProvider(sender);

    String messageId =
        provider.send(
            new OutboundEmail(
                "Masternova <no-reply@masternova.dev>",
                "asha@example.com",
                "Confirm your email",
                "<p>Hi <strong>Asha</strong></p>",
                "Hi Asha",
                Map.of("List-Unsubscribe", "<https://masternova.dev/u>")));

    assertThat(messageId).startsWith("<").endsWith(">");

    RestClient api =
        RestClient.create(
            "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + "/api/v1");
    JsonNode list = api.get().uri("/messages").retrieve().body(JsonNode.class);
    assertThat(list.get("total").asInt()).isEqualTo(1);
    String id = list.get("messages").get(0).get("ID").asString();

    JsonNode message = api.get().uri("/message/{id}", id).retrieve().body(JsonNode.class);
    assertThat(message.get("Subject").asString()).isEqualTo("Confirm your email");
    assertThat(message.get("HTML").asString()).contains("<strong>Asha</strong>");
    assertThat(message.get("Text").asString()).contains("Hi Asha");
    assertThat(message.get("MessageID").asString()).isEqualTo(messageId.replaceAll("[<>]", ""));

    JsonNode headers = api.get().uri("/message/{id}/headers", id).retrieve().body(JsonNode.class);
    assertThat(headers.get("List-Unsubscribe").get(0).asString())
        .isEqualTo("<https://masternova.dev/u>");
  }
}
