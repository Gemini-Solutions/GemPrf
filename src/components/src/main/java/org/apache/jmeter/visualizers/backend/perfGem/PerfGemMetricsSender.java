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

/**
 * PerfGem Sender interface
 *
 * @since 3.2
 */
interface PerfGemMetricsSender {

    /**
     * One data point in PerfGem is represented by a measurement name, a tag
     * set and a field set ( optionally a timestamp )
     */
    final class MetricTuple {
        String measurement;
        String tag;
        String field;
        long timestamp;
        MetricTuple(String measurement, String tag, String field, long timestamp) {
            this.measurement = measurement;
            this.tag = tag;
            this.field = field;
            this.timestamp = timestamp;
        }
    }

    /**
     * @param measurement name of the PerfGem measurement
     * @param tag         tag set for PerfGem (N.B. Needs to start with a comma)
     * @param field       field set for PerfGem
     */
    public void addMetric(String measurement, String tag, String field);

    /**
     * @param measurement name of the PerfGem measurement
     * @param tag         tag set for PerfGem (N.B. Needs to start with a comma)
     * @param field       field set for PerfGem
     * @param timestamp   timestamp for PerfGem
     */
    public void addMetric(String measurement, String tag, String field, long timestamp);

    /**
     * Write metrics to PerfGem with HTTP API with PerfGem's Line Protocol
     */
    public void writeAndSendMetrics();

    /**
     * Setup sender using PerfGemUrl
     *
     * @param PerfGemUrl   url pointing to PerfGem
     * @param bridgeToken authorization token to PerfGem
     * @param username username for Perf Backend
     * @throws Exception when setup fails
     */
    public void setup(String PerfGemUrl, String bridgeToken,String username) throws Exception; // NOSONAR

    /**
     * Destroy sender
     */
    public void destroy();

}
