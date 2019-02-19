/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.agent.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Queues;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.agent.collector.Collector;
import org.glowroot.agent.util.RateLimitedLogger;
import org.glowroot.agent.util.ThreadFactories;
import org.glowroot.common.util.OnlyUsedByTests;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.EumClientSpanMessage.EumClientSpan;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class EumSpanCollector {

    private static final Logger logger = LoggerFactory.getLogger(EumSpanCollector.class);

    // back pressure on writing captured data to disk/network
    private static final int PENDING_LIMIT = 60;

    private final Collector collector;
    private final ExecutorService dedicatedExecutor;

    private final BlockingQueue<String> pending = Queues.newLinkedBlockingQueue(PENDING_LIMIT);

    private final RateLimitedLogger backPressureLogger =
            new RateLimitedLogger(EumSpanCollector.class);

    private volatile boolean closed;

    public EumSpanCollector(Collector collector) {
        this.collector = collector;
        dedicatedExecutor = Executors
                .newSingleThreadExecutor(ThreadFactories.create("Glowroot-EUM-Span-Collector"));
        dedicatedExecutor.execute(new SpanCollectorLoop());
    }

    @OnlyUsedByTests
    public void close() throws InterruptedException {
        closed = true;
        // shutdownNow() is needed here to send interrupt to collector thread
        dedicatedExecutor.shutdownNow();
        if (!dedicatedExecutor.awaitTermination(10, SECONDS)) {
            throw new IllegalStateException("Could not terminate executor");
        }
    }

    public void collectEumDataFromQueryString(String queryString) {
        if (!pending.offer(queryString)) {
            backPressureLogger.warn("not storing eum data because of an excessive backlog"
                    + " of {} eum data already waiting to be stored", PENDING_LIMIT);
        }
    }

    private class SpanCollectorLoop implements Runnable {

        @Override
        public void run() {
            while (!closed) {
                try {
                    String queryString = pending.take();
                    EumClientSpan clientTransaction = toClientSpan(queryString);
                    if (clientTransaction != null) {
                        collector.collectEumClientSpans(ImmutableList.of(clientTransaction));
                    }
                } catch (InterruptedException e) {
                    // probably shutdown requested (see close method above)
                    logger.debug(e.getMessage(), e);
                } catch (Throwable e) {
                    // log and continue processing
                    logger.error(e.getMessage(), e);
                }
            }
        }

        private @Nullable EumClientSpan toClientSpan(String queryString) {
            QueryStringDecoder decoder = new QueryStringDecoder(queryString);
            Map<String, List<String>> parameters = decoder.parameters();
            List<String> traceId = parameters.get("trace-id");
            if (traceId == null || traceId.isEmpty()) {
                logger.warn("missing trace-id in eum query string: {}", queryString);
                return null;
            }
            List<String> durationMillisText = parameters.get("duration-millis");
            if (durationMillisText == null || durationMillisText.isEmpty()) {
                logger.warn("missing duration-millis in eum query string: {}", queryString);
                return null;
            }
            int durationMillis;
            try {
                durationMillis = Integer.parseInt(durationMillisText.get(0));
            } catch (NumberFormatException e) {
                logger.warn("invalid duration-millis in eum query string: {}", queryString);
                return null;
            }
            return EumClientSpan.newBuilder()
                    .setDistributedTraceId(traceId.get(0))
                    .setDurationNanos(MILLISECONDS.toNanos(durationMillis))
                    .build();
        }
    }
}
