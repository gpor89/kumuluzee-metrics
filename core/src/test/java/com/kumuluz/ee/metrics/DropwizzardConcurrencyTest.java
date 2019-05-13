package com.kumuluz.ee.metrics;

import com.codahale.metrics.MetricRegistry;

import org.junit.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DropwizzardConcurrencyTest {

    private static final String METRIC_NAME = "myMetric";

    private final MetricRegistry dwMetricRegistry = new MetricRegistry();

    @Test
    public void dropwizzardTest() throws Exception {
        final AtomicBoolean fail = new AtomicBoolean();
        fail.set(false);

        int n = 1000;
        ExecutorService es = Executors.newCachedThreadPool();
        final CyclicBarrier gate = new CyclicBarrier(n + 1);

        List<Thread> threadList = new LinkedList<Thread>();
        for (int j = 0; j < n; j++) {
            threadList.add(new Thread() {
                public void run() {
                    try {
                        gate.await();
                    } catch (InterruptedException e) {
                        fail.set(true);
                    } catch (BrokenBarrierException e) {
                        fail.set(true);
                    }
                    try {
                        dwMetricRegistry.counter(METRIC_NAME).inc();
                    } catch (Exception e) {
                        fail.set(true);
                    }
                }
            });
        }

        for (Thread t : threadList) {
            es.execute(t);
        }

        //executes counts concurrently
        gate.await();

        es.shutdown();
        boolean finished = es.awaitTermination(5, TimeUnit.SECONDS);
        assertTrue("Threads not finished in given time", finished);

        final Long count = dwMetricRegistry.getCounters().get(METRIC_NAME).getCount();

        assertEquals("Metric count is not equal", Long.valueOf(n), count);
        assertFalse(fail.get());
    }

}
