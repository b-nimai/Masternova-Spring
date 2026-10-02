package com.masternova.patterns.observer;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.patterns.observer.EventBus.PublishResult;
import com.masternova.patterns.observer.EventBus.Subscription;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventBusTest {

  sealed interface CourseEvent permits CoursePublished, CourseArchived {}

  record CoursePublished(String courseId) implements CourseEvent {}

  record CourseArchived(String courseId) implements CourseEvent {}

  record UserRegistered(String userId) {}

  @Test
  void observersOnlyReceiveTheTypesTheyAskedFor() {
    EventBus bus = new EventBus();
    List<String> searchIndex = new ArrayList<>();
    List<String> welcomeEmails = new ArrayList<>();
    bus.subscribe(CoursePublished.class, e -> searchIndex.add("index " + e.courseId()));
    bus.subscribe(UserRegistered.class, e -> welcomeEmails.add("welcome " + e.userId()));

    bus.publish(new CoursePublished("c1"));
    bus.publish(new UserRegistered("asha"));

    assertThat(searchIndex).containsExactly("index c1");
    assertThat(welcomeEmails).containsExactly("welcome asha");
  }

  @Test
  void subscribingToASupertypeReceivesEverySubtype() {
    EventBus bus = new EventBus();
    List<CourseEvent> audit = new ArrayList<>();
    bus.subscribe(CourseEvent.class, audit::add);

    bus.publish(new CoursePublished("c1"));
    bus.publish(new CourseArchived("c1"));

    assertThat(audit).containsExactly(new CoursePublished("c1"), new CourseArchived("c1"));
  }

  @Test
  void theSubjectDoesNotCareWhetherAnyoneListens() {
    assertThat(new EventBus().publish(new CoursePublished("c1")).delivered()).isZero();
  }

  @Test
  void aCancelledSubscriptionStopsReceiving() {
    EventBus bus = new EventBus();
    List<String> seen = new ArrayList<>();
    Subscription subscription = bus.subscribe(CoursePublished.class, e -> seen.add(e.courseId()));

    bus.publish(new CoursePublished("c1"));
    subscription.cancel();
    bus.publish(new CoursePublished("c2"));

    assertThat(seen).containsExactly("c1");
  }

  @Test
  void oneFailingObserverDoesNotStopTheOthers() {
    EventBus bus = new EventBus();
    List<String> seen = new ArrayList<>();
    bus.subscribe(CoursePublished.class, e -> seen.add("first"));
    bus.subscribe(CoursePublished.class, e -> {
      throw new IllegalStateException("search cluster down");
    });
    bus.subscribe(CoursePublished.class, e -> seen.add("third"));

    PublishResult result = bus.publish(new CoursePublished("c1"));

    assertThat(seen).containsExactly("first", "third");
    assertThat(result.delivered()).isEqualTo(2);
    assertThat(result.failures()).singleElement().hasFieldOrPropertyWithValue("message", "search cluster down");
  }
}
