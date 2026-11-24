/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.jboss.netty.handler.codec.http.HttpCodecUtil.normalizeAndGetContentLength;
import static org.junit.Assert.*;

public class HttpCodecUtilTest {

    @Test
    public void normalizeAndGetContentLengthEmpty() {
        testNormalizeAndGetContentLengthInvalidContentLength("");
    }

    @Test
    public void normalizeAndGetContentLengthNotANumber() {
        testNormalizeAndGetContentLengthInvalidContentLength("foo");
    }

    @Test
    public void normalizeAndGetContentLengthNegative() {
        testNormalizeAndGetContentLengthInvalidContentLength("-1");
    }

    @Test
    public void normalizeAndGetContentLengthPositiveSign() {
        testNormalizeAndGetContentLengthInvalidContentLength("+1");
    }

    @Test
    public void normalizeAndGetContentLengthNoFields() {
        assertEquals(-1, normalizeAndGetContentLength(Collections.<String>emptyList(), false, false));
    }

    @Test
    public void normalizeAndGetContentLengthValid() {
        assertEquals(42, normalizeAndGetContentLength(Collections.singletonList("42"), false, false));
    }

    @Test
    public void normalizeAndGetContentLengthDuplicates() {
        try {
            normalizeAndGetContentLength(Arrays.asList("42", "42"), false, false);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            normalizeAndGetContentLength(Collections.singletonList("42, 42"), false, false);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        assertEquals(42, normalizeAndGetContentLength(Arrays.asList("42", "42"), false, true));
        assertEquals(42, normalizeAndGetContentLength(Collections.singletonList("42, 42"), false, true));
        try {
            normalizeAndGetContentLength(Arrays.asList("42", "43"), false, true);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void normalizeAndGetContentLengthDuplicatesHttp10() {
        // HTTP/1.0 or earlier does not reject multiple Content-Length values; the first one is used.
        assertEquals(42, normalizeAndGetContentLength(Arrays.asList("42", "43"), true, false));
    }

    private static void testNormalizeAndGetContentLengthInvalidContentLength(String contentLengthField) {
        try {
            normalizeAndGetContentLength(Collections.singletonList(contentLengthField), false, false);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
