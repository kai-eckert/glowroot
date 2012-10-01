/**
 * Copyright 2011-2012 the original author or authors.
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
package org.informantproject.local.trace;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.Thread.State;

import org.informantproject.core.trace.MergedStackTree;
import org.informantproject.core.trace.Trace;
import org.informantproject.core.util.ByteStream;
import org.junit.Test;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class TraceSnapshotsTest {

    @Test
    public void shouldStoreVeryLargeMergedStackTree() throws IOException {
        // given
        MergedStackTree mergedStackTree = new MergedStackTree();
        Trace trace = mock(Trace.class);
        when(trace.getCoarseMergedStackTree()).thenReturn(mergedStackTree);
        // StackOverflowError was previously occurring somewhere around 1300 stack trace elements
        // using a 1mb thread stack size so testing with 10,000 here just to be sure
        StackTraceElement[] stackTrace = new StackTraceElement[10000];
        for (int i = 0; i < stackTrace.length; i++) {
            stackTrace[i] = new StackTraceElement(TraceSnapshotsTest.class.getName(), "method" + i,
                    "TraceSnapshotsTest.java", 100 + 10 * i);
        }
        mergedStackTree.addToStackTree(stackTrace, State.RUNNABLE);
        // when
        ByteStream mergedStackTreeByteStream = TraceSnapshotService.getMergedStackTree(trace
                .getCoarseMergedStackTree());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertThat(mergedStackTreeByteStream).isNotNull();
        mergedStackTreeByteStream.writeTo(baos);
        // then don't blow up with StackOverflowError
        // (and an extra verification just to make sure the test was valid)
        assertThat(baos.size()).isGreaterThan(1000000);
    }
}
