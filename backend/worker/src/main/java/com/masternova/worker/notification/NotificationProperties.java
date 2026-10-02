package com.masternova.worker.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Notification settings, bound from {@code masternova.notification.*}.
 *
 * @param webUrl the Angular app's public URL — every link in an email starts here
 * @param from the sender, e.g. {@code Masternova <no-reply@masternova.dev>}
 * @param provider which {@code MailProvider} adapter sends (smtp | resend)
 * @param resend Resend HTTP API settings (only read when provider = resend)
 */
@Validated
@ConfigurationProperties(prefix = "masternova.notification")
public record NotificationProperties(
    @NotNull URI webUrl,
    @DefaultValue("Masternova <no-reply@masternova.dev>") @NotBlank String from,
    @DefaultValue("smtp") @NotNull Provider provider,
    @Valid @DefaultValue Resend resend) {

  public enum Provider {
    SMTP,
    RESEND
  }

  public record Resend(
      @DefaultValue("https://api.resend.com") @NotNull URI baseUrl, String apiKey) {}
}
