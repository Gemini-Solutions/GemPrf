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
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.util.EntityUtils;
import org.apache.jmeter.report.utils.MetricUtils;
import org.apache.jmeter.util.JMeterUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * PerfGem sender base on The Line Protocol.
 * <p>
 * The Line Protocol is a text based format for writing points to PerfGem.
 * Syntax:
 * <pre>
 * weather,location=us-midwest temperature=82 1465839830100400200
 * |      -------------------- --------------  |
 * |               |             |             |
 * +-----------+--------+-+---------+-+---------+
 * |measurement|,tag_set| |field_set| |timestamp|
 * +-----------+--------+-+---------+-+---------+
 * </pre>
 * Each line, separated by the newline character, represents a single point in PerfGem.
 * The Line Protocol is whitespace sensitive.
 * <p>
 * See https://docs.PerfGemdata.com/PerfGem/v1.7/write_protocols/line_protocol_tutorial/
 *
 * @since 3.2
 */
class HttpMetricsSender extends AbstractPerfGemMetricsSender {
    private static final Logger log = LoggerFactory.getLogger(HttpMetricsSender.class);

    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    private static final String AUTHORIZATION_HEADER_VALUE = "Token ";

    private final Object lock = new Object();

    private List<MetricTuple> metrics = new ArrayList<>();

    private HttpPost httpRequest;
    private CloseableHttpAsyncClient httpClient;
    private URL url;
    private String token;
    private String user;

    private Future<HttpResponse> lastRequest;

    HttpMetricsSender() {
        super();
    }

    /**
     * The HTTP API is the primary means of writing data into PerfGem, by
     * sending POST requests to the /write endpoint. Initiate the HttpClient
     * client with a HttpPost request from PerfGem url
     *
     * @param PerfGemUrl   example : http://localhost:8086/write?db=myd&rp=one_week
     * @param bridgeToken example: my-token
     * @see PerfGemMetricsSender#setup(String, String, String)
     */
    @Override
    public void setup(String PerfGemUrl, String bridgeToken, String username) throws Exception {
        // Create I/O reactor configuration
        IOReactorConfig ioReactorConfig = IOReactorConfig
                .custom()
                .setIoThreadCount(1)
                .setConnectTimeout(JMeterUtils.getPropDefault("backend_PerfGem.connection_timeout", 1000))
                .setSoTimeout(JMeterUtils.getPropDefault("backend_PerfGem.socket_timeout", 3000))
                .build();
        // Create a custom I/O reactor
        ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(ioReactorConfig);

        // Create a connection manager with custom configuration.
        PoolingNHttpClientConnectionManager connManager =
                new PoolingNHttpClientConnectionManager(ioReactor);

        httpClient = HttpAsyncClientBuilder.create()
                .setConnectionManager(connManager)
                .setMaxConnPerRoute(2)
                .setMaxConnTotal(2)
                .setUserAgent("ApacheJMeter" + JMeterUtils.getJMeterVersion())
                .disableCookieManagement()
                .disableConnectionState()
                .build();
        url = new URL(PerfGemUrl);
        token = bridgeToken;
        user = username;
        httpRequest = createRequest(url, token,user);
        httpClient.start();
    }

    /**
     * @param url   {@link URL} PerfGem Url
     * @param token PerfGem authorization token
     * @param username
     * @return {@link HttpPost}
     * @throws URISyntaxException
     */
    private HttpPost createRequest(URL url, String token, String username) throws URISyntaxException {
        RequestConfig defaultRequestConfig = RequestConfig.custom()
                .setConnectTimeout(JMeterUtils.getPropDefault("backend_PerfGem.connection_timeout", 1000))
                .setSocketTimeout(JMeterUtils.getPropDefault("backend_PerfGem.socket_timeout", 3000))
                .setConnectionRequestTimeout(JMeterUtils.getPropDefault("backend_PerfGem.connection_request_timeout", 100))
                .build();

        HttpPost currentHttpRequest = new HttpPost(url.toURI());
        currentHttpRequest.setConfig(defaultRequestConfig);
        if (StringUtils.isNotBlank(token)) {
            currentHttpRequest.setHeader(AUTHORIZATION_HEADER_NAME, AUTHORIZATION_HEADER_VALUE + token);
        }
        log.debug("Created PerfGemMetricsSender with url: {}", url);
        return currentHttpRequest;
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

    public JSONArray JsonMaker(String dbData){
          String[] fulllist = dbData.split("\\n");

          JSONArray master = new JSONArray();
          int idx=0;
          for (String string1 : fulllist) {

              String[] list = string1.split(",");



              JSONObject j = new JSONObject();
              for(int i = 1; i < list.length ; i++){



                  String[] in = list[i].split("=");
                  if(in[0].contains(".0")){
                      j.put(in[0].substring(0,in[0].length()-2),in[1]);
                  }
                  else {
                      j.put(in[0], in[1]);
                  }
              }
              master.put(j);
              idx=idx+1;

          }
    return  master;

    }
    public JSONObject JsonFin(JSONArray jjson){
        JSONObject tempJson = new JSONObject();
        JSONObject returnJson = new JSONObject();

        tempJson = jjson.getJSONObject(jjson.length()-1);

        returnJson.put("application",tempJson.get("application"));
        returnJson.put("p_id",tempJson.get("p_id"));
        returnJson.put("env",tempJson.get("environment"));
        returnJson.put("user",tempJson.get("user"));
        jjson.getJSONObject(jjson.length()-1).remove("p_id");
        jjson.getJSONObject(jjson.length()-1).remove("user");
        jjson.getJSONObject(jjson.length()-1).remove("environment");
        returnJson.put("perf_metrix",jjson);

//        System.out.println(returnJson.toString());



        return  returnJson;

    }

    @Override
    public void writeAndSendMetrics() {
        List<MetricTuple> copyMetrics;
        synchronized (lock) {
            if (metrics.isEmpty()) {
                return;
            }
            copyMetrics = metrics;
            metrics = new ArrayList<>(copyMetrics.size());
        }
        writeAndSendMetrics(copyMetrics);
    }

    private void writeAndSendMetrics(List<MetricTuple> copyMetrics) {
        try {
            if (httpRequest == null) {
                httpRequest = createRequest(url, token, user);
            }

            StringBuilder sb = new StringBuilder(copyMetrics.size() * 35);
            for (MetricTuple metric : copyMetrics) {
                // Add TimeStamp in nanosecond from epoch ( default in PerfGem )
                sb.append(metric.measurement)
                        .append(metric.tag)
                        .append(" ") //$NON-NLS-1$
                        .append(metric.field)
                        .append(" ")
                        .append(metric.timestamp)
                        .append("000000")
                        .append("\n"); //$NON-NLS-1$
            }

            String data = sb.toString();



            JSONArray finalJson = new JSONArray();
            JSONObject finalfinalJson = new JSONObject();
            finalJson = JsonMaker(data);
            finalfinalJson = JsonFin(finalJson);
//            System.out.println(finalfinalJson.toString(4));


            StringEntity entity = new StringEntity(finalfinalJson.toString());
            try{
                httpRequest.setHeader("Content-type", "application/json");
                httpRequest.setEntity(entity);
                httpRequest.setHeader("bridgeToken",token);
                httpRequest.setHeader("username",user);

            }
            catch(Throwable t){
                System.out.println(t);
            }

            lastRequest = httpClient.execute(httpRequest, new FutureCallback<HttpResponse>() {
                @Override
                public void completed(final HttpResponse response) {
                    int code = response.getStatusLine().getStatusCode();

                    try {
                        System.out.println("Res--->"+EntityUtils.toString(response.getEntity()));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    /*
                     * If your write request received HTTP
                     * 204 No Content: it was a success!
                     * 4xx: PerfGem could not understand the request.
                     * 5xx: The system is overloaded or significantly impaired.
                     */
                    if (MetricUtils.isSuccessCode(code)) {
                        if (log.isDebugEnabled()) {
                            log.debug("Success, number of metrics written: {}", copyMetrics.size());
                        }
                    } else {
                        log.error("Error writing metrics to PerfGem Url: {}, responseCode: {}, responseBody: {}", url, code, getBody(response));
                    }
                }

                @Override
                public void failed(final Exception ex) {
                    log.error("failed to send data to PerfGem server.", ex);
                }

                @Override
                public void cancelled() {
                    log.warn("Request to PerfGem server was cancelled");
                }
            });
        } catch (URISyntaxException ex) {
            log.error(ex.getMessage(), ex);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param response HttpResponse
     * @return String entity Body if any
     */
    private static String getBody(final HttpResponse response) {
        String body = "";
        try {
            if (response != null && response.getEntity() != null) {
                body = EntityUtils.toString(response.getEntity());
            }
        } catch (Exception e) { // NOSONAR
            // NOOP
        }
        return body;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void destroy() {
        // Give some time to send last metrics before shutting down
        log.info("Destroying ");
        try {
            lastRequest.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error("Error waiting for last request to be send to PerfGem", e);
        }
        if (httpRequest != null) {
            httpRequest.abort();
        }
        IOUtils.closeQuietly(httpClient, null);
    }

}
