package com.masternova.patterns.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.patterns.proxy.AccessControlledRevenueReport.Viewer;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProxyTest {

  @BeforeEach
  void resetLoads() {
    RemoteVideoManifest.LOADS.set(0);
  }

  @Test
  void virtualProxyLoadsOnlyWhatIsUsedAndOnlyOnce() {
    List<VideoManifest> coursePage =
        List.of("l1", "l2", "l3", "l4").stream()
            .<VideoManifest>map(id -> new LazyVideoManifest(() -> new RemoteVideoManifest(id)))
            .toList();

    assertThat(RemoteVideoManifest.LOADS).hasValue(0); // the page rendered: nothing loaded yet

    coursePage.get(1).playlist();
    coursePage.get(1).playlist(); // second play: cached

    assertThat(RemoteVideoManifest.LOADS).hasValue(1); // 1 load instead of 4 (or 5)
    assertThat(coursePage.get(1).playlist()).contains("lecture l2");
  }

  @Test
  void protectionProxyGuardsTheRealReport() {
    Map<String, Long> earnings = Map.of("asha", 7_995_00L, "ravi", 3_498_00L);
    RevenueReport real = earnings::get;
    AtomicReference<Viewer> viewer = new AtomicReference<>(new Viewer("asha", false));
    RevenueReport guarded = new AccessControlledRevenueReport(real, viewer::get);

    assertThat(guarded.totalFor("asha")).isEqualTo(7_995_00L); // her own: allowed
    assertThatThrownBy(() -> guarded.totalFor("ravi")).hasMessageContaining("may not see");

    viewer.set(new Viewer("admin-1", true));
    assertThat(guarded.totalFor("ravi")).isEqualTo(3_498_00L); // admins see everything
  }

  @Test
  void dynamicProxyWorksForAnyInterfaceWithOneHandler() {
    List<String> log = new ArrayList<>();
    RevenueReport report = TimingProxy.timed(RevenueReport.class, id -> 42L, log);
    VideoManifest manifest = TimingProxy.timed(VideoManifest.class, () -> "#EXTM3U", log);

    report.totalFor("asha");
    manifest.playlist();

    assertThat(log).containsExactly("totalFor timed", "playlist timed");
    assertThat(Proxy.isProxyClass(report.getClass())).isTrue(); // generated at runtime
  }

  @Test
  void dynamicProxyRethrowsTheRealException() {
    RevenueReport failing = id -> {
      throw new IllegalArgumentException("unknown instructor " + id);
    };
    RevenueReport proxy = TimingProxy.timed(RevenueReport.class, failing, new ArrayList<>());

    assertThatThrownBy(() -> proxy.totalFor("x"))
        .isInstanceOf(IllegalArgumentException.class) // not InvocationTargetException
        .hasMessage("unknown instructor x");
  }
}
