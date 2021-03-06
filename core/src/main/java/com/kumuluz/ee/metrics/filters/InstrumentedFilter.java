/*
 *  Copyright (c) 2014-2017 Kumuluz and/or its affiliates
 *  and other contributors as indicated by the @author tags and
 *  the contributor list.
 *
 *  Licensed under the MIT License (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  https://opensource.org/licenses/MIT
 *
 *  The software is provided "AS IS", WITHOUT WARRANTY OF ANY KIND, express or
 *  implied, including but not limited to the warranties of merchantability,
 *  fitness for a particular purpose and noninfringement. in no event shall the
 *  authors or copyright holders be liable for any claim, damages or other
 *  liability, whether in an action of contract, tort or otherwise, arising from,
 *  out of or in connection with the software or the use or other dealings in the
 *  software. See the License for the specific language governing permissions and
 *  limitations under the License.
*/
package com.kumuluz.ee.metrics.filters;

import com.kumuluz.ee.metrics.producers.MetricRegistryProducer;
import org.eclipse.microprofile.metrics.*;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Filter used for web instrumentation.
 *
 * @author Urban Malc
 * @author Aljaž Blažej
 * @since 1.0.0
 */
public class InstrumentedFilter implements Filter {

    public static final String PARAM_INSTRUMENTATION_NAME = InstrumentedFilter.class.getName() + ".instrumentationName";
    public static final String PARAM_METER_STATUS_CODES = InstrumentedFilter.class.getName() + ".meterStatusCodes";

    private ConcurrentMap<Integer, Meter> metersByStatusCode;
    private Meter otherMeter;
    private Meter timeoutsMeter;
    private Meter errorsMeter;
    private Counter activeRequests;
    private Timer requestTimer;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

        String instrumentationName = filterConfig.getInitParameter(PARAM_INSTRUMENTATION_NAME);
        List<Integer> meterStatusCodes = Arrays.stream(
                filterConfig.getInitParameter(PARAM_METER_STATUS_CODES).split(","))
                .map(Integer::parseInt)
                .collect(Collectors.toList());

        MetricRegistry metricsRegistry = MetricRegistryProducer.getVendorRegistry();

        String metricPrefix = "webInstrumentation." + instrumentationName;

        this.metersByStatusCode = new ConcurrentHashMap<>(meterStatusCodes.size());
        for (Integer sc : meterStatusCodes) {
            Metadata meterMetadata = new Metadata(MetricRegistry.name(metricPrefix, "status", sc.toString()),
                    sc + " responses on " + instrumentationName,
                    "Number of responses with status code " + sc + " on " + instrumentationName,
                    MetricType.METERED, MetricUnits.NONE);
            metersByStatusCode.put(sc, metricsRegistry.meter(meterMetadata));
        }

        Metadata otherMetadata = new Metadata(MetricRegistry.name(metricPrefix, "status", "other"),
                "Other responses on " + instrumentationName,
                "Number of responses with other status codes on " + instrumentationName,
                MetricType.METERED, MetricUnits.NONE);
        this.otherMeter = metricsRegistry.meter(otherMetadata);

        Metadata timeoutsMetadata = new Metadata(MetricRegistry.name(metricPrefix, "timeouts"),
                "Timeouts on " + instrumentationName,
                "Number of timeouts on " + instrumentationName,
                MetricType.METERED, MetricUnits.NONE);
        this.timeoutsMeter = metricsRegistry.meter(timeoutsMetadata);

        Metadata errorsMetadata = new Metadata(MetricRegistry.name(metricPrefix, "errors"),
                "Errors on " + instrumentationName,
                "Number of errors on " + instrumentationName,
                MetricType.METERED, MetricUnits.NONE);
        this.errorsMeter = metricsRegistry.meter(errorsMetadata);

        Metadata activeRequestsMetadata = new Metadata(MetricRegistry.name(metricPrefix, "activeRequests"),
                "Active requests on " + instrumentationName,
                "Number of active requests on " + instrumentationName,
                MetricType.METERED, MetricUnits.NONE);
        this.activeRequests = metricsRegistry.counter(activeRequestsMetadata);

        Metadata timerMetadata = new Metadata(MetricRegistry.name(metricPrefix, "response"),
                instrumentationName + " response timer",
                "Response timer for " + instrumentationName,
                MetricType.TIMER, MetricUnits.NANOSECONDS);
        this.requestTimer = metricsRegistry.timer(timerMetadata);

    }

    @Override
    public void destroy() {

    }

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        final StatusExposingServletResponse wrappedResponse =
                new StatusExposingServletResponse((HttpServletResponse) response);
        activeRequests.inc();
        final Timer.Context context = requestTimer.time();
        boolean error = false;
        try {
            chain.doFilter(request, wrappedResponse);
        } catch (IOException | RuntimeException | ServletException e) {
            error = true;
            throw e;
        } finally {
            if (!error && request.isAsyncStarted()) {
                request.getAsyncContext().addListener(new AsyncResultListener(context));
            } else {
                context.stop();
                activeRequests.dec();
                if (error) {
                    errorsMeter.mark();
                } else {
                    markMeterForStatusCode(wrappedResponse.getStatus());
                }
            }
        }
    }

    private void markMeterForStatusCode(int status) {
        final Meter metric = metersByStatusCode.get(status);
        if (metric != null) {
            metric.mark();
        } else {
            otherMeter.mark();
        }
    }

    private class AsyncResultListener implements AsyncListener {
        private Timer.Context context;
        private boolean done = false;

        public AsyncResultListener(Timer.Context context) {
            this.context = context;
        }

        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            if (!done) {
                HttpServletResponse suppliedResponse = (HttpServletResponse) event.getSuppliedResponse();
                context.stop();
                activeRequests.dec();
                markMeterForStatusCode(suppliedResponse.getStatus());
            }
        }

        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
            context.stop();
            activeRequests.dec();
            timeoutsMeter.mark();
            done = true;
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {
            context.stop();
            activeRequests.dec();
            errorsMeter.mark();
            done = true;
        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {

        }
    }
}
