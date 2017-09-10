package ru.ifmo.mpp.jmh;

import org.openjdk.jmh.annotations.Threads;

@Threads(2)
public class TwoThreadedBenchmarkScope extends SingleThreadedBenchmarkScope {
}
