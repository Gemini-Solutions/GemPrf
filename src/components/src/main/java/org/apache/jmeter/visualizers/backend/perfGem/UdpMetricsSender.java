/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jmeter.visualizers.backend.perfGem;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PerfGem sender base on The Line Protocol. <br>
 * The Line Protocol is a text based format for writing points to PerfGem. <br>
 * Syntax : <br>
 * <code>
 * &lt;measurement&gt;[,&lt;tag_key&gt;=&lt;tag_value&gt;[,&lt;tag_key&gt;=&lt;tag_value&gt;]] &lt;field_key&gt;=&lt;field_value&gt;[,&lt;field_key&gt;=
 * &lt;field_value&gt;] [&lt;timestamp&gt;]
 * </code><br>
 * Each line, separated by the newline character, represents a single point in PerfGem.<br>
 * Line Protocol is whitespace sensitive.
 */
class UdpMetricsSender extends AbstractPerfGemMetricsSender {

    private static final Logger log = LoggerFactory.getLogger(UdpMetricsSender.class);

    private final Object lock = new Object();

    private InetAddress hostAddress;
    private int udpPort;

    private List<MetricTuple> metrics = new ArrayList<>();

    UdpMetricsSender() {
        super();
    }

    @Override
    public void setup(String perfGemUrl, String bridgeToken, String username) throws Exception {
        try {
            log.debug("Setting up with url:{}", perfGemUrl);
            String[] urlComponents = perfGemUrl.split(":");
            if(urlComponents.length == 2) {
                hostAddress = InetAddress.getByName(urlComponents[0]);
                udpPort = Integer.parseInt(urlComponents[1]);
            } else {
                throw new IllegalArgumentException(
                        "perfGem url '"+perfGemUrl+"' is wrong. The format should be <host/ip>:<port>");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "perfGem url '"+perfGemUrl+"' is wrong. The format should be <host/ip>:<port>", e);
        }
    }

    @Override
    public void addMetric(String measurement, String tag, String field) {
        addMetric(measurement, tag, field, System.currentTimeMillis());
    }

    @Override
    public void addMetric(String measurement, String tag, String field, long timestamp) {
        synchronized (lock) {
            metrics.add(new MetricTuple(measurement, tag, field, timestamp));
        }
    }

    @Override
    public void writeAndSendMetrics() {
        List<MetricTuple> tempMetrics;
        synchronized (lock) {
            if (metrics.isEmpty()) {
                return;
            }
            tempMetrics = metrics;
            metrics = new ArrayList<>(tempMetrics.size());
        }
        final List<MetricTuple> copyMetrics = tempMetrics;

        if (!copyMetrics.isEmpty()) {
            StringBuilder sb = new StringBuilder(copyMetrics.size() * 35);
            for (MetricTuple metric : copyMetrics) {
                // Add TimeStamp in nanosecond from epoch ( default in PerfGem
                // )
                sb.append(metric.measurement).append(metric.tag).append(" ") //$NON-NLS-1$
                    .append(metric.field).append(" ").append(metric.timestamp + "000000").append("\n"); //$NON-NLS-3$
            }

            try (DatagramSocket ds = new DatagramSocket()) {
                byte[] buf = sb.toString().getBytes(StandardCharsets.UTF_8);
                DatagramPacket dp = new DatagramPacket(buf, buf.length, this.hostAddress, this.udpPort);
                ds.send(dp);
            } catch (SocketException e) {
                log.error("Cannot open udp port!", e);
                return;
            } catch (IOException e) {
                log.error("Error in transferring udp package", e);
            } finally {
                // We drop metrics in all cases
                copyMetrics.clear();
            }
        }
    }

    @Override
    public void destroy() {
        // NOOP
    }
}
