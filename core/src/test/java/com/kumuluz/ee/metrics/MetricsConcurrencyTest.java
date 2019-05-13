package com.kumuluz.ee.metrics;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Arquillian.class)
public class MetricsConcurrencyTest {

    private static final Metadata METRIC = new Metadata("myMetric", MetricType.COUNTER);

    static {
        METRIC.setReusable(false);
    }

    @Inject
    private MetricRegistry kumuluzMetricRegistry;

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class);
    }

    @Test
    public void kumuluzTest() throws Exception {
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
                        System.err.println(e);
                        fail.set(true);
                    } catch (BrokenBarrierException e) {
                        System.err.println(e);
                        fail.set(true);
                    }
                    try {
                        kumuluzMetricRegistry.counter(METRIC).inc();
                    } catch (Exception e) {
                        System.err.println(e);
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

        final Long count = kumuluzMetricRegistry.getCounters().get(METRIC.getName()).getCount();

        //this assertion fails since there is a problem in library implementation, not handling concurrency proper
        assertEquals("Metric count is not equal", Long.valueOf(n), count);
        assertFalse(fail.get());
    }


}
