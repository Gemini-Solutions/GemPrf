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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.visualizers.backend.BackendListenerClient;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link BackendListenerClient} to write the response times
 * of every sample to PerfGem. If more "raw" information is required in PerfGem
 * then this class can be extended or another BackendListener
 * {@link PerfGemBackendListenerClient} can be used to send aggregate information
 * to PerfGem.
 *
 * @since 5.3
 */
public class PerfGemRawBackendListenerClient implements BackendListenerClient {

    private static final Logger log = LoggerFactory.getLogger(PerfGemRawBackendListenerClient.class);

    private static final Object LOCK = new Object();

    private static final String TAG_OK = "ok";
    private static final String TAG_KO = "ko";
    private static final String DEFAULT_MEASUREMENT = "jmeter";

    private static final Map<String, String> DEFAULT_ARGS = new LinkedHashMap<>();

    static {
//        DEFAULT_ARGS.put("perfGemMetricsSender", org.apache.jmeter.visualizers.backend.perfGem.HttpMetricsSender.class.getName());
//        DEFAULT_ARGS.put("perfGemUrl", "http://host_to_change:8086/write?db=jmeter");
        DEFAULT_ARGS.put("bridgeToken", "");
        DEFAULT_ARGS.put("username","");
//        DEFAULT_ARGS.put("measurement", DEFAULT_MEASUREMENT);
    }

    private PerfGemMetricsSender PerfGemMetricsManager;
    private String measurement;

    public PerfGemRawBackendListenerClient() {
        // default constructor
    }

    /**
     * Used for testing.
     *
     * @param sender the {@link PerfGemMetricsSender} to use
     */
    public PerfGemRawBackendListenerClient(PerfGemMetricsSender sender) {
        PerfGemMetricsManager = sender;
    }

    @Override
    public void setupTest(BackendListenerContext context) throws Exception {
        initPerfGemMetricsManager(context);
        measurement = context.getParameter("measurement", DEFAULT_MEASUREMENT);
    }

    private void initPerfGemMetricsManager(BackendListenerContext context) throws Exception {
        PerfGemMetricsManager = Class
                .forName(context.getParameter("perfGemMetricsSender", org.apache.jmeter.visualizers.backend.perfGem.HttpMetricsSender.class.getName()))
                .asSubclass(PerfGemMetricsSender.class)
                .getDeclaredConstructor()
                .newInstance();

        PerfGemMetricsManager.setup(
                context.getParameter("perfGemUrl", "https://apis.gemecosystem.com/perfcheck"),
                context.getParameter("bridgeToken"),
                context.getParameter("username"));
    }

    @Override
    public void teardownTest(BackendListenerContext context) {
        PerfGemMetricsManager.destroy();
    }

    @Override
    public void handleSampleResults(
            List<SampleResult> sampleResults, BackendListenerContext context) {
        log.debug("Handling {} sample results", sampleResults.size());
        synchronized (LOCK) {
            for (SampleResult sampleResult : sampleResults) {
                addMetricFromSampleResult(sampleResult);
            }
            PerfGemMetricsManager.writeAndSendMetrics();
        }
    }

    private void addMetricFromSampleResult(SampleResult sampleResult) {
        String tags = "," + createTags(sampleResult);
        String fields = createFields(sampleResult);
        long timestamp = sampleResult.getTimeStamp();

        PerfGemMetricsManager.addMetric(measurement, tags, fields, timestamp);
    }

    private String createTags(SampleResult sampleResult) {
        boolean isError = sampleResult.getErrorCount() != 0;
        String status = isError ? TAG_KO : TAG_OK;
        // remove surrounding quotes and spaces from sample label
        String label = StringUtils.strip(sampleResult.getSampleLabel(), "\" ");
        String transaction = AbstractPerfGemMetricsSender.tagToStringValue(label);
        return "status=" + status
                + ",transaction=" + transaction;
    }

    private String createFields(SampleResult sampleResult) {
        long duration = sampleResult.getTime();
        long latency = sampleResult.getLatency();
        long connectTime = sampleResult.getConnectTime();
        return "duration=" + duration
                + ",ttfb=" + latency
                + ",connectTime=" + connectTime;
    }

    @Override
    public Arguments getDefaultParameters() {
        Arguments arguments = new Arguments();
        DEFAULT_ARGS.forEach(arguments::addArgument);
        return arguments;
    }
}
