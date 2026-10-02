package com.masternova.java.concurrency;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Five ways to count enrollments from many threads — one broken, four correct.
 *
 * <p>Study note: {@code patterns/java/07-concurrency-and-virtual-threads.md} §2.
 */
public final class Counters {

  private Counters() {}

  /** Common shape so the test can hammer every implementation the same way. */
  public interface Counter {
    void increment();

    long value();
  }

  /**
   * ❌ BROKEN: {@code count++} is THREE steps — read, add 1, write. Two threads can both read 41
   * and both write 42: one enrollment is lost ("lost update", a race condition).
   */
  public static final class UnsafeCounter implements Counter {
    private long count;

    @Override
    public void increment() {
      count++;
    }

    @Override
    public long value() {
      return count;
    }
  }

  /** ✅ {@code synchronized}: one thread at a time inside the method (a monitor lock on this). */
  public static final class SynchronizedCounter implements Counter {
    private long count;

    @Override
    public synchronized void increment() {
      count++;
    }

    @Override
    public synchronized long value() { // ⭐ reads must synchronize too — for VISIBILITY
      return count;
    }
  }

  /**
   * ✅ {@link AtomicLong}: a lock-free compare-and-swap (CAS) in hardware. The default choice for a
   * single shared number.
   */
  public static final class AtomicCounter implements Counter {
    private final AtomicLong count = new AtomicLong();

    @Override
    public void increment() {
      count.incrementAndGet();
    }

    @Override
    public long value() {
      return count.get();
    }
  }

  /**
   * ✅ {@link LongAdder}: spreads updates over several cells — scales better than AtomicLong
   * under HEAVY write contention (metrics, hit counters); reading sums the cells.
   */
  public static final class AdderCounter implements Counter {
    private final LongAdder count = new LongAdder();

    @Override
    public void increment() {
      count.increment();
    }

    @Override
    public long value() {
      return count.sum();
    }
  }

  /**
   * ✅ {@link ReentrantLock}: an explicit lock — like synchronized, plus tryLock with a timeout,
   * fairness, and multiple conditions. ⭐ ALWAYS unlock in finally.
   */
  public static final class LockCounter implements Counter {
    private final ReentrantLock lock = new ReentrantLock();
    private long count;

    @Override
    public void increment() {
      lock.lock();
      try {
        count++;
      } finally {
        lock.unlock(); // ⭐ or an exception leaves the lock held forever
      }
    }

    @Override
    public long value() {
      lock.lock();
      try {
        return count;
      } finally {
        lock.unlock();
      }
    }
  }
}
