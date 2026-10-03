package com.masternova.patterns.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.patterns.state.Lifecycle.Action;
import com.masternova.patterns.state.Lifecycle.EnumState;
import com.masternova.patterns.state.Lifecycle.IllegalTransition;
import com.masternova.patterns.state.Lifecycle.State;
import com.masternova.patterns.state.Lifecycle.Status;
import org.junit.jupiter.api.Test;

class LifecycleTest {

  /** ⭐ The three implementations must be the SAME machine: compare them on every pair. */
  @Test
  void allThreeStylesAgreeOnEveryStateActionPair() {
    for (Status from : Status.values()) {
      for (Action action : Action.values()) {
        Status bySwitch = outcome(() -> Lifecycle.bySwitch(from, action));
        Status byEnum = outcome(() -> Status.valueOf(EnumState.valueOf(from.name()).on(action).name()));
        Status bySealed = outcome(() -> State.of(from).on(action).status());

        assertThat(byEnum).as("%s --%s-->", from, action).isEqualTo(bySwitch);
        assertThat(bySealed).as("%s --%s-->", from, action).isEqualTo(bySwitch);
      }
    }
  }

  @Test
  void reviewIsNotOptional() {
    assertThatThrownBy(() -> State.of(Status.DRAFT).publish()).isInstanceOf(IllegalTransition.class);
  }

  @Test
  void archivedIsTerminal() {
    for (Action action : Action.values()) {
      assertThatThrownBy(() -> State.of(Status.ARCHIVED).on(action))
          .isInstanceOf(IllegalTransition.class);
    }
  }

  @Test
  void aPathThroughTheMachine() {
    State state = State.of(Status.DRAFT).submit().publish().archive();

    assertThat(state).isEqualTo(new Lifecycle.Archived()); // records: states compare by value
  }

  /** null = illegal, so outcomes can be compared. */
  private static Status outcome(java.util.function.Supplier<Status> transition) {
    try {
      return transition.get();
    } catch (IllegalTransition e) {
      return null;
    }
  }
}
