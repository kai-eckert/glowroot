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

import java.util.Map;

import com.google.common.collect.MapMaker;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.agent.config.ConfigService;
import org.glowroot.agent.plugin.api.config.BooleanProperty;
import org.glowroot.agent.plugin.api.config.ConfigListener;
import org.glowroot.agent.plugin.api.config.StringProperty;
import org.glowroot.common.config.EumConfig;

public class EumConfigServiceImpl
        implements org.glowroot.agent.plugin.api.config.EumConfigService, ConfigListener {

    private static final Logger logger = LoggerFactory.getLogger(EumConfigServiceImpl.class);

    private final ConfigService configService;

    // cache for fast read access
    // visibility is provided by memoryBarrier in org.glowroot.config.ConfigService
    private @MonotonicNonNull EumConfig eumConfig;

    private final EnabledPropertyImpl enabledProperty = new EnabledPropertyImpl();
    private final ReportingUrlPropertyImpl reportingUrlProperty = new ReportingUrlPropertyImpl();

    private final Map<ConfigListener, Boolean> configListeners =
            new MapMaker().weakKeys().makeMap();

    public static EumConfigServiceImpl create(ConfigService configService) {
        EumConfigServiceImpl configServiceImpl = new EumConfigServiceImpl(configService);
        configService.addPluginConfigListener(configServiceImpl);
        configService.addConfigListener(configServiceImpl);
        return configServiceImpl;
    }

    private EumConfigServiceImpl(ConfigService configService) {
        this.configService = configService;
        configListeners.put(enabledProperty, true);
        configListeners.put(reportingUrlProperty, true);
    }

    @Override
    public BooleanProperty getEnabledProperty() {
        return enabledProperty;
    }

    @Override
    public StringProperty getReportingUrlProperty() {
        return reportingUrlProperty;
    }

    @Override
    public void registerConfigListener(ConfigListener listener) {
        if (listener == null) {
            logger.error("registerConfigListener(): argument 'listener' must be non-null");
            return;
        }
        configService.addConfigListener(listener);
        listener.onChange();
        configService.writeMemoryBarrier();
    }

    @Override
    public void onChange() {
        this.eumConfig = configService.getEumConfig();
        for (ConfigListener configListener : configListeners.keySet()) {
            configListener.onChange();
        }
        configService.writeMemoryBarrier();
    }

    private class EnabledPropertyImpl implements BooleanProperty, ConfigListener {
        // visibility is provided by memoryBarrier in outer class
        private boolean value;
        private EnabledPropertyImpl() {
            value = eumConfig.enabled();
        }
        @Override
        public boolean value() {
            return value;
        }
        @Override
        public void onChange() {
            value = eumConfig.enabled();
        }
    }

    private class ReportingUrlPropertyImpl implements StringProperty, ConfigListener {
        // visibility is provided by memoryBarrier in outer class
        private String value;
        private ReportingUrlPropertyImpl() {
            value = eumConfig.reportingUrl();
        }
        @Override
        public String value() {
            return value;
        }
        @Override
        public void onChange() {
            value = eumConfig.reportingUrl();
        }
    }
}
