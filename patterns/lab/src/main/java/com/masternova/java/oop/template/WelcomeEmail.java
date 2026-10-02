package com.masternova.java.oop.template;

/** Sent after signup. Uses the default footer. */
public final class WelcomeEmail extends EmailTemplate {

  @Override
  public String subject() {
    return "Welcome to Masternova";
  }

  @Override
  protected String body() {
    return "Your account is ready. Browse the catalog to start your first course.";
  }

  @Override
  protected String unsubscribeTopic() {
    return "onboarding";
  }
}
