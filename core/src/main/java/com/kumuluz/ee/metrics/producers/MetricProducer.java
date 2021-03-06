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
package com.kumuluz.ee.metrics.producers;

import com.kumuluz.ee.metrics.utils.AnnotationMetadata;
import org.eclipse.microprofile.metrics.*;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import javax.interceptor.Interceptor;

/**
 * Producers for Microprofile metrics.
 *
 * @author Urban Malc
 * @author Aljaž Blažej
 * @since 1.0.0
 */
@Alternative
@ApplicationScoped
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
public class MetricProducer {

    @Inject
    private MetricRegistry applicationRegistry;

    @Produces
    public Meter produceMeter(InjectionPoint injectionPoint) {
        return applicationRegistry.meter(AnnotationMetadata.buildProducerMetadata(injectionPoint));
    }

    @Produces
    public Timer produceTimer(InjectionPoint injectionPoint) {
        return applicationRegistry.timer(AnnotationMetadata.buildProducerMetadata(injectionPoint));
    }

    @Produces
    public Counter produceCounter(InjectionPoint injectionPoint) {
        return applicationRegistry.counter(AnnotationMetadata.buildProducerMetadata(injectionPoint));
    }

    @Produces
    public Histogram produceHistogram(InjectionPoint injectionPoint) {
        return applicationRegistry.histogram(AnnotationMetadata.buildProducerMetadata(injectionPoint));
    }

    @SuppressWarnings("unchecked")
    @Produces
    public <T> Gauge<T> produceGauge(InjectionPoint injectionPoint) {
        Metadata m = AnnotationMetadata.buildProducerMetadata(injectionPoint);

        return () -> (T) applicationRegistry.getGauges().get(m.getName()).getValue();
    }
}
