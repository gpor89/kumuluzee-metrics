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
package com.kumuluz.ee.metrics;

import com.kumuluz.ee.configuration.utils.ConfigurationUtil;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;

/**
 * Initializes Graphite reporter.
 *
 * @author Urban Malc, Aljaž Blažej
 */
@ApplicationScoped
public class GraphiteInitiator {

    private void initialiseBean(@Observes @Initialized(ApplicationScoped.class) Object init) {

        ConfigurationUtil configurationUtil = ConfigurationUtil.getInstance();

        // start graphite reporter
        boolean usePickle = configurationUtil.getBoolean("kumuluzee.metrics.graphite.pickle").orElse(true);
        String address = configurationUtil.get("kumuluzee.metrics.graphite.address").orElse("127.0.0.1");
        int port = configurationUtil.getInteger("kumuluzee.metrics.graphite.port")
                .orElse(usePickle ? 2004 : 2003);
        long periodSeconds = configurationUtil.getInteger("kumuluzee.metrics.graphite.period-s").orElse(60);

        new KumuluzEEGraphiteReporter(address, port, periodSeconds, usePickle);

    }
}