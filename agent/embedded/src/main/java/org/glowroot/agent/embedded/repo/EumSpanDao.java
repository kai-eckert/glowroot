/*
 * Copyright 2018-2019 the original author or authors.
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
package org.glowroot.agent.embedded.repo;

import java.sql.SQLException;
import java.util.List;

import com.google.common.collect.ImmutableList;

import org.glowroot.agent.embedded.util.DataSource;
import org.glowroot.agent.embedded.util.ImmutableColumn;
import org.glowroot.agent.embedded.util.ImmutableIndex;
import org.glowroot.agent.embedded.util.Schemas.Column;
import org.glowroot.agent.embedded.util.Schemas.ColumnType;
import org.glowroot.agent.embedded.util.Schemas.Index;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.EumClientSpanMessage.EumClientSpan;
import org.glowroot.wire.api.model.CollectorServiceOuterClass.EumServerSpanMessage.EumServerSpan;

public class EumSpanDao {

    private static final ImmutableList<Column> eumServerSpanColumns = ImmutableList.<Column>of(
            ImmutableColumn.of("distributed_trace_id", ColumnType.VARCHAR),
            ImmutableColumn.of("capture_time", ColumnType.VARCHAR),
            ImmutableColumn.of("server_span", ColumnType.VARBINARY));

    private static final ImmutableList<Column> eumClientSpanColumns = ImmutableList.<Column>of(
            ImmutableColumn.of("distributed_trace_id", ColumnType.VARCHAR),
            ImmutableColumn.of("capture_time", ColumnType.VARCHAR),
            ImmutableColumn.of("client_span", ColumnType.VARBINARY));

    private static final ImmutableList<Index> eumServerSpanIndexes = ImmutableList.<Index>of(
            ImmutableIndex.of("eum_server_span_idx", ImmutableList.of("distributed_trace_id")),
            ImmutableIndex.of("eum_server_span_capture_time_idx",
                    ImmutableList.of("capture_time")));

    private static final ImmutableList<Index> eumClientSpanIndexes = ImmutableList.<Index>of(
            ImmutableIndex.of("eum_client_span_idx", ImmutableList.of("distributed_trace_id")));

    private final DataSource dataSource;

    public EumSpanDao(DataSource dataSource) throws SQLException {
        this.dataSource = dataSource;
        dataSource.syncTable("eum_server_span", eumServerSpanColumns);
        dataSource.syncIndexes("eum_server_span", eumServerSpanIndexes);
        dataSource.syncTable("eum_client_span", eumClientSpanColumns);
        dataSource.syncIndexes("eum_client_span", eumClientSpanIndexes);
    }

    public void storeServerSpans(List<EumServerSpan> serverSpans) throws SQLException {
        for (EumServerSpan serverSpan : serverSpans) {
            dataSource.update("insert into eum_server_span (distributed_trace_id, server_span)"
                    + " values (?, ?, ?)", serverSpan.getDistributedTraceId(),
                    serverSpan.toByteArray());
        }
    }

    public void storeClientSpans(List<EumClientSpan> clientSpans) throws SQLException {
        for (EumClientSpan clientSpan : clientSpans) {
            dataSource.update("insert into eum_client_span (distributed_trace_id, client_span)"
                    + " values (?, ?, ?)", clientSpan.getDistributedTraceId(),
                    clientSpan.toByteArray());
        }
    }

    public void deleteBefore(long captureTime) throws SQLException {
        dataSource.deleteBefore("eum_server_span", captureTime);
        dataSource.deleteBefore("eum_client_span", captureTime);
    }
}
