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
package org.glowroot.agent.plugin.servlet.util;

import java.io.PrintWriter;
import java.io.Writer;

import org.glowroot.agent.plugin.api.checker.MonotonicNonNull;

public class EumPrintWriter extends PrintWriter {

    // closing tags are not allowed to have spaces prior to the tag name, but are allowed to have
    // spaces after the tag name, see https://www.w3.org/TR/html5/syntax.html#end-tags
    private static final String TAG = "</head";

    private final String injectionContent;

    private int currMatchLength;

    private @MonotonicNonNull StringBuilder extraSpaces;

    private boolean alreadyInjected;

    public EumPrintWriter(Writer delegate, String injectionContent) {
        super(delegate);
        this.injectionContent = injectionContent;
    }

    private MatchResult match(int c) {
        if (currMatchLength == TAG.length()) {
            if (c == '>') {
                return MatchResult.LAST;
            } else if (c != ' ' && c != '\t' && c != '\n' && c != '\r' && c != '\f') {
                currMatchLength = 0;
                return MatchResult.NO;
            } else {
                if (extraSpaces == null) {
                    extraSpaces = new StringBuilder();
                }
                extraSpaces.append((char) c);
                return MatchResult.MIDDLE;
            }
        } else if (c == TAG.charAt(currMatchLength)) {
            if (currMatchLength++ == 0) {
                return MatchResult.FIRST;
            } else if (currMatchLength == TAG.length() && extraSpaces != null) {
                // just in case needs reset
                extraSpaces.setLength(0);
            }
            return MatchResult.MIDDLE;
        } else {
            currMatchLength = 0;
            return MatchResult.NO;
        }
    }

    private void inject() {
        super.write(injectionContent, 0, injectionContent.length());
        writePartialMatch();
        super.write('>');
        alreadyInjected = true;
        currMatchLength = 0;
    }

    private void writePartialMatch() {
        super.write(TAG, 0, currMatchLength);
        if (extraSpaces != null) {
            super.write(extraSpaces.toString(), 0, extraSpaces.length());
        }
    }

    // not overriding flush() because sending or not sending an incomplete closing tag should not
    // have any impact on the monitored application

    @Override
    public void close() {
        if (currMatchLength > 0) {
            super.write(TAG, 0, currMatchLength);
            currMatchLength = 0;
        }
        super.close();
    }

    @Override
    public void write(int c) {
        if (alreadyInjected) {
            super.write(c);
            return;
        }
        int priorMatchLength = currMatchLength;
        MatchResult matchResult = match(c);
        if (matchResult == MatchResult.LAST) {
            inject();
        } else if (matchResult == MatchResult.NO) {
            if (priorMatchLength > 0) {
                super.write(TAG, 0, priorMatchLength);
            }
            super.write(c);
        }
    }

    @Override
    public void write(char[] buf, int off, int len) {
        if (alreadyInjected) {
            super.write(buf, off, len);
            return;
        }
        int priorMatchedLength = currMatchLength;
        MatchResult matchResult = MatchResult.NO;
        int matchStartIndex = -1;
        int matchEndIndex = -1;
        for (int i = 0; i < len; i++) {
            matchResult = match(buf[off + i]);
            if (matchResult == MatchResult.LAST) {
                matchEndIndex = i + 1;
                break;
            } else if (matchResult == MatchResult.NO) {
                matchStartIndex = -1;
            } else if (matchResult == MatchResult.FIRST) {
                matchStartIndex = i;
            }
        }
        if (matchResult == MatchResult.LAST) {
            if (matchStartIndex == -1) {
                // match was a continuation of previous write
                inject();
                super.write(buf, off + matchEndIndex, len - matchEndIndex);
            } else {
                if (priorMatchedLength > 0) {
                    super.write(TAG, 0, priorMatchedLength);
                }
                super.write(buf, off, matchStartIndex);
                inject();
                super.write(buf, off + matchEndIndex, len - matchEndIndex);
            }
        } else if (matchResult == MatchResult.NO) {
            if (priorMatchedLength > 0) {
                super.write(TAG, 0, priorMatchedLength);
            }
            super.write(buf, off, len);
        } else {
            // FIRST or MIDDLE
            if (matchStartIndex == -1) {
                // match was a continuation of previous write
            } else {
                int charsBeforeMatch = matchStartIndex - off;
                super.write(buf, off, charsBeforeMatch);
            }
        }
    }

    @Override
    public void write(String s, int off, int len) {
        if (alreadyInjected) {
            super.write(s, off, len);
            return;
        }
        int priorMatchedLength = currMatchLength;
        MatchResult matchResult = MatchResult.NO;
        int matchStartIndex = -1;
        int matchEndIndex = -1;
        for (int i = 0; i < len; i++) {
            matchResult = match(s.charAt(off + i));
            if (matchResult == MatchResult.LAST) {
                matchEndIndex = i + 1;
                break;
            } else if (matchResult == MatchResult.NO) {
                matchStartIndex = -1;
            } else if (matchResult == MatchResult.FIRST) {
                matchStartIndex = i;
            }
        }
        if (matchResult == MatchResult.LAST) {
            if (matchStartIndex == -1) {
                // match was a continuation of previous write
                inject();
                super.write(s, off + matchEndIndex, len - matchEndIndex);
            } else {
                if (priorMatchedLength > 0) {
                    super.write(TAG, 0, priorMatchedLength);
                }
                super.write(s, off, matchStartIndex);
                inject();
                super.write(s, off + matchEndIndex, len - matchEndIndex);
            }
        } else if (matchResult == MatchResult.NO) {
            if (priorMatchedLength > 0) {
                super.write(TAG, 0, priorMatchedLength);
            }
            super.write(s, off, len);
        } else {
            // FIRST or MIDDLE
            if (matchStartIndex == -1) {
                // match was a continuation of previous write
            } else {
                int charsBeforeMatch = matchStartIndex - off;
                super.write(s, off, charsBeforeMatch);
            }
        }
    }

    private static enum MatchResult {
        FIRST, MIDDLE, LAST, NO
    }
}
