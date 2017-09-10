package ru.ifmo.mpp.jmh;


import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;


@State(Scope.Benchmark)
@Threads(1)
public class SingleThreadedBenchmarkScope extends SingleThreadedThreadScope {
}
