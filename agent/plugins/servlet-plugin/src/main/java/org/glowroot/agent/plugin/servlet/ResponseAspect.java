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
package org.glowroot.agent.plugin.servlet;

import java.io.PrintWriter;

import org.glowroot.agent.plugin.api.Agent;
import org.glowroot.agent.plugin.api.checker.Nullable;
import org.glowroot.agent.plugin.api.config.BooleanProperty;
import org.glowroot.agent.plugin.api.config.ConfigListener;
import org.glowroot.agent.plugin.api.config.EumConfigService;
import org.glowroot.agent.plugin.api.weaving.BindReceiver;
import org.glowroot.agent.plugin.api.weaving.BindReturn;
import org.glowroot.agent.plugin.api.weaving.IsEnabled;
import org.glowroot.agent.plugin.api.weaving.Mixin;
import org.glowroot.agent.plugin.api.weaving.OnReturn;
import org.glowroot.agent.plugin.api.weaving.Pointcut;
import org.glowroot.agent.plugin.servlet.util.EumPrintWriter;

public class ResponseAspect {

    private static EumConfigService eumConfigService = Agent.getEumConfigService();

    private static BooleanProperty eumEnabled = eumConfigService.getEnabledProperty();

    private static final String DEFAULT_EUM_SCRIPT = "<script>"
            + "(function(i,s,o,g,r,a,m){"
            + "  i['EumObject']=r;"
            + "  i[r]=i[r]||function(){(i[r].q=i[r].q||[]).push(arguments)};"
            + "  i[r].l=1*new Date();"
            + "  a=s.createElement(o);"
            + "  m=s.getElementsByTagName(o)[0];"
            + "  a.async=1;"
            + "  if(g[g.length-1]!='/')"
            + "    g+='/';"
            + "  g+='--glowroot-eum';"
            + "  a.src=g+'.js';"
            + "  m.parentNode.insertBefore(a,m);"
            + "  i[r]('reportingUrl',g)"
            + "})(window,document,'script',location.pathname,'gteum')"
            + "</script>".replace(" ", "");

    private static final String NON_DEFAULT_EUM_SCRIPT = "<script>"
            + "(function(i,s,o,g,r,a,m){"
            + "  i['EumObject']=r;"
            + "  i[r]=i[r]||function(){(i[r].q=i[r].q||[]).push(arguments)};"
            + "  i[r].l=1*new Date();"
            + "  a=s.createElement(o);"
            + "  m=s.getElementsByTagName(o)[0];"
            + "  a.async=1;"
            + "  a.src=g+'.js';"
            + "  m.parentNode.insertBefore(a,m);"
            + "  i[r]('reportingUrl',g)"
            + "})(window,document,'script','{{reportingUrl}}','gteum')"
            + "</script>".replace(" ", "");

    private static String eumScript = "";

    static {
        eumConfigService.registerConfigListener(new ConfigListener() {
            @Override
            public void onChange() {
                String reportingUrl = eumConfigService.getReportingUrlProperty().value();
                if (reportingUrl.isEmpty()) {
                    eumScript = DEFAULT_EUM_SCRIPT;
                } else {
                    eumScript = NON_DEFAULT_EUM_SCRIPT.replace("{{reportingUrl}}", reportingUrl);
                }
            }
        });
    }

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin("javax.servlet.ServletResponse")
    public abstract static class ServletResponseImpl implements ServletResponseMixin {

        private transient @Nullable PrintWriter glowroot$writer;
        private transient @Nullable PrintWriter glowroot$eumWriter;

        @Override
        public @Nullable PrintWriter glowroot$getWriter() {
            return glowroot$writer;
        }

        @Override
        public void glowroot$setWriter(@Nullable PrintWriter writer) {
            glowroot$writer = writer;
        }

        @Override
        public @Nullable PrintWriter glowroot$getEumWriter() {
            return glowroot$eumWriter;
        }

        @Override
        public void glowroot$setEumWriter(@Nullable PrintWriter eumWriter) {
            glowroot$eumWriter = eumWriter;
        }
    }

    // the method names are verbose since they will be mixed in to existing classes
    public interface ServletResponseMixin {

        @Nullable
        PrintWriter glowroot$getWriter();

        void glowroot$setWriter(@Nullable PrintWriter writer);

        @Nullable
        PrintWriter glowroot$getEumWriter();

        void glowroot$setEumWriter(@Nullable PrintWriter eumWriter);
    }

    @Pointcut(className = "javax.servlet.ServletResponse", methodName = "getWriter",
            methodParameterTypes = {}, nestingGroup = "servlet-inner-call")
    public static class GetWriterAdvice {
        @IsEnabled
        public static boolean isEnabled() {
            return eumEnabled.value();
        }
        @OnReturn
        public static @Nullable PrintWriter onReturn(@BindReturn @Nullable PrintWriter writer,
                @BindReceiver ServletResponseMixin response) {
            if (writer == null) {
                return null;
            }
            if (response.glowroot$getWriter() == writer) {
                return response.glowroot$getEumWriter();
            } else {
                PrintWriter eumWriter = new EumPrintWriter(writer, eumScript);
                response.glowroot$setEumWriter(eumWriter);
                response.glowroot$setWriter(writer);
                return eumWriter;
            }
        }
    }
}
