package ru.ifmo.mpp.jmh;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(5)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@State(Scope.Thread)
@Threads(1)
public class SingleThreadedThreadScope {

    long value;
    int p01, p02, p03, p04, p05, p06, p07, p08;
    int p11, p12, p13, p14, p15, p16, p17, p18;
    volatile long volatileValue; // no false sharing
    int q01, q02, q03, q04, q05, q06, q07, q08;
    int q11, q12, q13, q14, q15, q16, q17, q18;
    ReentrantLock lock = new ReentrantLock();
    int r01, r02, r03, r04, r05, r06, r07, r08;
    int r11, r12, r13, r14, r15, r16, r17, r18;

    @Benchmark
    public long basicReader() {
        return value;
    }

    @Benchmark
    public long basicWriter() {
        return ++value;
    }

    @Benchmark
    public long volatileReader() {
        return volatileValue;
    }

    @Benchmark
    public long volatileWriter() {
        return ++volatileValue;
    }

    @Benchmark
    public synchronized long syncedReader() {
        return value;
    }

    @Benchmark
    public synchronized long syncedWriter() {
        return ++value;
    }

    @Benchmark
    public long lockingReader() {
        lock.lock();
        try {
            return value;
        } finally {
            lock.unlock();
        }
    }

    @Benchmark
    public long lockingWriter() {
        lock.lock();
        try {
            return ++value;
        } finally {
            lock.unlock();
        }
    }
}
