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

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.embedder.CodecEmbedderException;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.util.CharsetUtil;

import org.junit.Test;

import static org.junit.Assert.*;

public class HttpMessageDecoderTest {

    @Test
    public void testHeaderWithNoValueAndMissingColon() {
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 0\r\n" +
                "Host:\r\n" +
                "netty.io\r\n\r\n";

        try {
            embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII));
            fail("Expected CodecEmbedderException");
        } catch (CodecEmbedderException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testContentLengthHeaderWithPositiveSign() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: +1\r\n\r\n" +
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderWithNegativeSign() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: -1\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderWithNegativeValue() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: -10\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderEmpty() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length:\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderNotANumber() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: foo\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeadersSameValue() {
        // When allowDuplicateContentLengths is false (default), even identical values should be rejected
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 0\r\n" +
                "Content-Length: 0\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeadersSameValueWithComma() {
        // When allowDuplicateContentLengths is false (default), even identical comma-separated values should be rejected
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 0, 0\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeadersDifferentValues() {
        // Peers picking a different one of the conflicting values would disagree on the body length.
        String requestStr = "POST /some/path HTTP/1.1\r\n" +
                "Content-Length: 1\r\n" +
                "Content-Length: 5\r\n\r\n" +
                "bbbbb";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderWithPositiveSignSmuggling() {
        // "+5" must not be read as a body length of 5: a front-end that rejects or ignores the sign
        // would otherwise disagree with this decoder about where the next request starts.
        String requestStr = "POST /some/path HTTP/1.1\r\n" +
                "Content-Length: +5\r\n\r\n" +
                "helloGET /smuggled HTTP/1.1\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testValidContentLength() {
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        String requestStr = "POST /some/path HTTP/1.1\r\n" +
                "Content-Length: 5\r\n\r\n" +
                "hello";
        assertTrue(embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpRequest request = (HttpRequest) embedder.poll();
        assertEquals("5", request.getHeader(HttpHeaders.Names.CONTENT_LENGTH));
        assertEquals("hello", request.getContent().toString(CharsetUtil.US_ASCII));
        assertNull(embedder.poll());
    }

    @Test
    public void testMultipleContentLengthHeadersSameValueAllowed() {
        String requestStr = "POST /some/path HTTP/1.1\r\n" +
                "Content-Length: 1\r\n" +
                "Content-Length: 1\r\n\r\n" +
                "b";
        testAllowedDuplicateContentLengths0(requestStr);
    }

    @Test
    public void testContentLengthHeaderSameValueWithCommaAllowed() {
        String requestStr = "POST /some/path HTTP/1.1\r\n" +
                "Content-Length: 1, 1\r\n\r\n" +
                "b";
        testAllowedDuplicateContentLengths0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeadersDifferentValuesAllowedDuplicates() {
        String requestStr = "POST /some/path HTTP/1.1\r\n" +
                "Content-Length: 1\r\n" +
                "Content-Length: 2\r\n\r\n" +
                "bb";
        DecoderEmbedder<Object> embedder =
                new DecoderEmbedder<Object>(new HttpRequestDecoder(4096, 8192, 8192, true));
        try {
            embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII));
            fail("Expected CodecEmbedderException");
        } catch (CodecEmbedderException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    private static void testAllowedDuplicateContentLengths0(String requestStr) {
        DecoderEmbedder<Object> embedder =
                new DecoderEmbedder<Object>(new HttpRequestDecoder(4096, 8192, 8192, true));
        assertTrue(embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpRequest request = (HttpRequest) embedder.poll();
        // The duplicated field-values are replaced by a single valid Content-Length field.
        assertEquals(1, request.getHeaders(HttpHeaders.Names.CONTENT_LENGTH).size());
        assertEquals("1", request.getHeader(HttpHeaders.Names.CONTENT_LENGTH));
        assertEquals("b", request.getContent().toString(CharsetUtil.US_ASCII));
        assertNull(embedder.poll());
    }

    private static void testInvalidHeaders0(String requestStr) {
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        try {
            embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII));
            fail("Expected CodecEmbedderException");
        } catch (CodecEmbedderException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }
}
