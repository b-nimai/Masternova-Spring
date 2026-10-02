package com.masternova.api.learning.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

/**
 * What a "proxy" physically is — first by hand with the JDK, then with Spring's ProxyFactory (the
 * machinery behind @Transactional, @Cacheable, @Async). Study note: {@code
 * patterns/java/09-spring-aop-and-proxies.md} §2.
 */
class ProxyMechanicsLearningTest {

  interface PriceService {
    long priceOf(String courseId);
  }

  static class CatalogPriceService implements PriceService {
    @Override
    public long priceOf(String courseId) {
      return 1_499_00;
    }
  }

  @Test
  void aJdkDynamicProxyIsAGeneratedClassImplementingTheInterface() {
    List<String> log = new ArrayList<>();
    PriceService target = new CatalogPriceService();

    // ⭐ Every call on the proxy goes to this handler, which decides what to do around the target.
    InvocationHandler handler =
        (proxy, method, args) -> {
          log.add("before " + method.getName());
          Object result = method.invoke(target, args);
          log.add("after " + method.getName());
          return result;
        };
    PriceService proxy =
        (PriceService)
            Proxy.newProxyInstance(
                PriceService.class.getClassLoader(), new Class<?>[] {PriceService.class}, handler);

    assertThat(proxy.priceOf("c1")).isEqualTo(1_499_00);
    assertThat(log).containsExactly("before priceOf", "after priceOf");
    assertThat(Proxy.isProxyClass(proxy.getClass())).isTrue();
    assertThat(proxy).isNotInstanceOf(CatalogPriceService.class); // it IS-A PriceService only
  }

  @Test
  void springUsesAJdkProxyForInterfacesAndCglibForClasses() {
    MethodInterceptor passThrough = invocation -> invocation.proceed();

    ProxyFactory jdk = new ProxyFactory(new CatalogPriceService());
    jdk.addInterface(PriceService.class);
    jdk.addAdvice(passThrough);
    Object jdkProxy = jdk.getProxy();

    ProxyFactory cglib = new ProxyFactory(new CatalogPriceService());
    cglib.setProxyTargetClass(true); // ⭐ Spring Boot's DEFAULT (spring.aop.proxy-target-class=true)
    cglib.addAdvice(passThrough);
    Object cglibProxy = cglib.getProxy();

    assertThat(AopUtils.isJdkDynamicProxy(jdkProxy)).isTrue();
    assertThat(AopUtils.isCglibProxy(cglibProxy)).isTrue();
    // A CGLIB proxy is a generated SUBCLASS of the target class:
    assertThat(cglibProxy).isInstanceOf(CatalogPriceService.class);
    assertThat(cglibProxy.getClass().getName()).contains("$$SpringCGLIB$$");
  }

  static final class FinalPriceService implements PriceService { // final: cannot be subclassed
    @Override
    public long priceOf(String courseId) {
      return 0;
    }
  }

  @Test
  void aFinalClassCannotGetAClassBasedProxy() {
    ProxyFactory factory = new ProxyFactory(new FinalPriceService());
    factory.setProxyTargetClass(true);
    factory.addAdvice((MethodInterceptor) invocation -> invocation.proceed());

    // ⭐ This is why JPA entities and @Transactional services must not be final (and why a
    //    record — always final — can't carry @Transactional methods).
    assertThatThrownBy(factory::getProxy)
        .isInstanceOf(AopConfigException.class)
        .hasMessageContaining("final");
  }
}
