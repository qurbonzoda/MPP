package ru.ifmo.mpp.jmh;

import org.openjdk.jmh.annotations.Threads;

@Threads(4)
public class FourThreadedBenchmarkScope extends SingleThreadedBenchmarkScope {
}
