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

import org.jboss.netty.buffer.ChannelBuffer;
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

    // ---------------------------------------------------------------------
    // CVE-2019-20445: a Content-Length header must not be accompanied by a
    // second Content-Length header, and a Content-Length header accompanied
    // by 'Transfer-Encoding: chunked' must be removed (RFC 7230 section 3.3.3).
    // Backport of netty/netty@8494b04 and netty/netty@6290346.
    // ---------------------------------------------------------------------

    @Test
    public void testMultipleContentLengthHeaders() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 1\r\n" +
                "Content-Length: 0\r\n\r\n" +
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeaders2() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 1\r\n" +
                "Connection: close\r\n" +
                "Content-Length: 0\r\n\r\n" +
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderWithCommaValue() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 1,1\r\n\r\n" +
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeadersWithFolding() {
        String requestStr = "POST / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Connection: close\r\n" +
                "Content-Length: 5\r\n" +
                "Content-Length:\r\n" +
                "\t6\r\n\r\n" +
                "123456";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderAndChunked() {
        String requestStr = "POST / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Connection: close\r\n" +
                "Content-Length: 5\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n" +
                "0\r\n\r\n";

        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        assertTrue(embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpRequest request = (HttpRequest) embedder.poll();
        assertTrue(request.isChunked());
        assertEquals(HttpHeaders.Values.CHUNKED, request.getHeader(HttpHeaders.Names.TRANSFER_ENCODING));
        assertFalse(request.containsHeader(HttpHeaders.Names.CONTENT_LENGTH));
        HttpChunk chunk = (HttpChunk) embedder.poll();
        assertTrue(chunk.isLast());
        assertNull(embedder.poll());
    }

    @Test
    public void testContentLengthHeaderAndChunkedSmuggling() {
        // The chunked body (TE overrides CL) carries a second request inside a chunk. The stale
        // Content-Length must not survive, or a peer forwarding the message would frame it by
        // Content-Length and see the smuggled request as a separate one.
        String requestStr = "POST / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Content-Length: 4\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n" +
                "1a\r\n" +
                "GET /smuggled HTTP/1.1\r\n\r\n" +
                "\r\n" +
                "0\r\n\r\n";

        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        assertTrue(embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpRequest request = (HttpRequest) embedder.poll();
        assertTrue(request.isChunked());
        assertFalse(request.containsHeader(HttpHeaders.Names.CONTENT_LENGTH));
        assertEquals(-1, HttpHeaders.getContentLength(request, -1));
        HttpChunk chunk = (HttpChunk) embedder.poll();
        assertEquals("GET /smuggled HTTP/1.1\r\n\r\n", chunk.getContent().toString(CharsetUtil.US_ASCII));
        assertTrue(((HttpChunk) embedder.poll()).isLast());
        assertNull(embedder.poll());
    }

    @Test
    public void testContentLengthHeaderAndChunkedResponse() {
        String responseStr = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: 5\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n" +
                "0\r\n\r\n";

        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpResponseDecoder());
        assertTrue(embedder.offer(ChannelBuffers.copiedBuffer(responseStr, CharsetUtil.US_ASCII)));
        HttpResponse response = (HttpResponse) embedder.poll();
        assertTrue(response.isChunked());
        assertEquals(HttpHeaders.Values.CHUNKED, response.getHeader(HttpHeaders.Names.TRANSFER_ENCODING));
        assertFalse(response.containsHeader(HttpHeaders.Names.CONTENT_LENGTH));
        HttpChunk chunk = (HttpChunk) embedder.poll();
        assertTrue(chunk.isLast());
        assertNull(embedder.poll());
    }

    // ---------------------------------------------------------------------
    // CVE-2026-42585: the 'chunked' transfer coding must be the final coding
    // of the Transfer-Encoding header and must not be applied more than once,
    // and requests whose final transfer coding is not 'chunked' must be
    // rejected (RFC 9112 sections 6.1 and 6.3).
    // Backport of netty/netty@485f11d, netty/netty@09f5588 and netty/netty@ca87393.
    // ---------------------------------------------------------------------

    @Test
    public void testChunkedNotLastInTransferEncoding() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Transfer-Encoding: chunked, identity\r\n" +
                "Content-Length: 1\r\n" +
                "Host: netty.io\r\n\r\n" +
                "a";
        testInvalidHeaders0(requestStr);
    }

    // Regression: the chunked-must-be-last check must not be gated on HTTP/1.1.
    @Test
    public void testChunkedNotLastInTransferEncodingHttp10() {
        String requestStr = "GET /some/path HTTP/1.0\r\n" +
                "Transfer-Encoding: chunked, identity\r\n" +
                "Content-Length: 1\r\n" +
                "Host: netty.io\r\n\r\n" +
                "a";
        testInvalidHeaders0(requestStr);
    }

    // Companion to the test above: confirms the fix isn't tied to identity being a no-op
    // by using a real trailing encoding (gzip) instead.
    @Test
    public void testChunkedNotLastInTransferEncodingHttp10NonNoOpEncoding() {
        String requestStr = "GET /some/path HTTP/1.0\r\n" +
                "Transfer-Encoding: chunked, gzip\r\n" +
                "Content-Length: 1\r\n" +
                "Host: netty.io\r\n\r\n" +
                "a";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testInvalidTransferEncodingFramingChunkedIdentityWithContentLength() {
        testInvalidTransferEncodingFraming("Transfer-Encoding: chunked, identity\r\nContent-Length: 1\r\n");
    }

    @Test
    public void testInvalidTransferEncodingFramingChunkedThenGzipMultiLine() {
        testInvalidTransferEncodingFraming("Transfer-Encoding: chunked\r\nTransfer-Encoding: gzip\r\n");
    }

    @Test
    public void testInvalidTransferEncodingFramingChunkedGzipTrailingComma() {
        testInvalidTransferEncodingFraming("Transfer-Encoding: chunked, gzip,\r\n");
    }

    @Test
    public void testInvalidTransferEncodingFramingChunkedXchunked() {
        testInvalidTransferEncodingFraming("Transfer-Encoding: chunked, xchunked\r\n");
    }

    @Test
    public void testInvalidTransferEncodingFramingGzipOnly() {
        testInvalidTransferEncodingFraming("Transfer-Encoding: gzip\r\n");
    }

    @Test
    public void testInvalidTransferEncodingFramingChunkedSpaceGzipWithContentLength() {
        testInvalidTransferEncodingFraming("Transfer-Encoding: chunked gzip\r\nContent-Length: 1\r\n");
    }

    @Test
    public void testInvalidTransferEncodingFramingChunkedTwice() {
        testInvalidTransferEncodingFraming("Transfer-Encoding: chunked, chunked\r\n");
    }

    @Test
    public void testInvalidTransferEncodingFramingChunkedGzipChunked() {
        testInvalidTransferEncodingFraming("Transfer-Encoding: chunked, gzip, chunked\r\n");
    }

    @Test
    public void testInvalidTransferEncodingFramingChunkedTwiceMultiLine() {
        testInvalidTransferEncodingFraming("Transfer-Encoding: chunked\r\nTransfer-Encoding: chunked\r\n");
    }

    @Test
    public void testInvalidTransferEncodingFramingChunkedTwiceMixedCase() {
        testInvalidTransferEncodingFraming("Transfer-Encoding: Chunked, , CHUNKED,\r\n");
    }

    @Test
    public void testInvalidTransferEncodingFramingChunkedWithParameter() {
        testInvalidTransferEncodingFraming("Transfer-Encoding: chunked;foo=bar\r\n");
    }

    private static void testInvalidTransferEncodingFraming(String headers) {
        testInvalidHeaders0("GET /some/path HTTP/1.1\r\n" + headers + "Host: netty.io\r\n\r\n");
    }

    @Test
    public void testChunkedLastInMultiLineTransferEncoding() {
        testChunkedLastInMultiLineTransferEncoding(
                "Transfer-Encoding: gzip\r\nTransfer-Encoding: chunked\r\n");
    }

    @Test
    public void testChunkedLastInMultiLineTransferEncodingTrailingComma() {
        testChunkedLastInMultiLineTransferEncoding(
                "Transfer-Encoding: gzip\r\nTransfer-Encoding: chunked,\r\n");
    }

    @Test
    public void testChunkedLastInMultiLineTransferEncodingEmptyElements() {
        testChunkedLastInMultiLineTransferEncoding(
                "Transfer-Encoding: chunked\r\nTransfer-Encoding: , \t,\r\n");
    }

    private static void testChunkedLastInMultiLineTransferEncoding(String transferEncodingHeaders) {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                transferEncodingHeaders +
                "Host: netty.io\r\n\r\n" +
                "0\r\n\r\n";
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        assertTrue(embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));

        HttpRequest request = (HttpRequest) embedder.poll();
        assertTrue(request.isChunked());
        assertEquals(2, request.getHeaders(HttpHeaders.Names.TRANSFER_ENCODING).size());
        HttpChunk chunk = (HttpChunk) embedder.poll();
        assertTrue(chunk.isLast());
        assertNull(embedder.poll());
    }

    @Test
    public void testChunkedNotLastInTransferEncodingResponse() {
        String responseStr = "HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding: chunked, gzip\r\n" +
                "Content-Length: 1\r\n\r\n" +
                "a";
        testInvalidResponseHeaders0(ChannelBuffers.copiedBuffer(responseStr, CharsetUtil.US_ASCII));
    }

    @Test
    public void testTransferCodingGzipResponseReadsUntilClose() {
        // Unlike requests, a response without a final chunked coding may still be framed by
        // reading until the connection is closed, so it must not be rejected.
        String responseStr = "HTTP/1.1 200 OK\r\n" +
                "Connection: close\r\n" +
                "Transfer-Encoding: gzip\r\n\r\n" +
                "hello";
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpResponseDecoder());
        assertTrue(embedder.offer(ChannelBuffers.copiedBuffer(responseStr, CharsetUtil.US_ASCII)));

        HttpResponse response = (HttpResponse) embedder.poll();
        assertNotNull(response);
        assertEquals("gzip", response.getHeader(HttpHeaders.Names.TRANSFER_ENCODING));
        assertFalse(response.containsHeader(HttpHeaders.Names.CONTENT_LENGTH));
        HttpChunk chunk = (HttpChunk) embedder.poll();
        assertEquals("hello", chunk.getContent().toString(CharsetUtil.US_ASCII));
    }

    // ---------------------------------------------------------------------
    // GHSA-wx5j-54mm-rqqq: header names must be bracketed by OWS only.
    // The decoder used to silently strip every Character.isWhitespace char,
    // which let an attacker smuggle control characters around a header name.
    // The strict OWS check now rejects 0x0c (FF) and 0x1c-0x1f (FS/GS/RS/US)
    // both before the name and between the name and the colon.
    // ---------------------------------------------------------------------

    @Test
    public void testRequestHeaderNameStartsWithControlChar1c() {
        testRequestHeaderNameStartsWithControlChar(0x1c);
    }

    @Test
    public void testRequestHeaderNameStartsWithControlChar1d() {
        testRequestHeaderNameStartsWithControlChar(0x1d);
    }

    @Test
    public void testRequestHeaderNameStartsWithControlChar1e() {
        testRequestHeaderNameStartsWithControlChar(0x1e);
    }

    @Test
    public void testRequestHeaderNameStartsWithControlChar1f() {
        testRequestHeaderNameStartsWithControlChar(0x1f);
    }

    @Test
    public void testRequestHeaderNameStartsWithControlChar0c() {
        testRequestHeaderNameStartsWithControlChar(0x0c);
    }

    private static void testRequestHeaderNameStartsWithControlChar(int controlChar) {
        ChannelBuffer requestBuffer = ChannelBuffers.dynamicBuffer();
        requestBuffer.writeBytes("GET /some/path HTTP/1.1\r\nHost: netty.io\r\n".getBytes(CharsetUtil.US_ASCII));
        requestBuffer.writeByte(controlChar);
        requestBuffer.writeBytes("Transfer-Encoding: chunked\r\n\r\n".getBytes(CharsetUtil.US_ASCII));
        testInvalidRequestHeaders0(requestBuffer);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar1c() {
        testRequestHeaderNameEndsWithControlChar(0x1c);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar1d() {
        testRequestHeaderNameEndsWithControlChar(0x1d);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar1e() {
        testRequestHeaderNameEndsWithControlChar(0x1e);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar1f() {
        testRequestHeaderNameEndsWithControlChar(0x1f);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar0c() {
        testRequestHeaderNameEndsWithControlChar(0x0c);
    }

    private static void testRequestHeaderNameEndsWithControlChar(int controlChar) {
        ChannelBuffer requestBuffer = ChannelBuffers.dynamicBuffer();
        requestBuffer.writeBytes("GET /some/path HTTP/1.1\r\nHost: netty.io\r\n".getBytes(CharsetUtil.US_ASCII));
        requestBuffer.writeBytes("Transfer-Encoding".getBytes(CharsetUtil.US_ASCII));
        requestBuffer.writeByte(controlChar);
        requestBuffer.writeBytes(": chunked\r\n\r\n".getBytes(CharsetUtil.US_ASCII));
        testInvalidRequestHeaders0(requestBuffer);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar1c() {
        testResponseHeaderNameStartsWithControlChar(0x1c);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar1d() {
        testResponseHeaderNameStartsWithControlChar(0x1d);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar1e() {
        testResponseHeaderNameStartsWithControlChar(0x1e);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar1f() {
        testResponseHeaderNameStartsWithControlChar(0x1f);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar0c() {
        testResponseHeaderNameStartsWithControlChar(0x0c);
    }

    private static void testResponseHeaderNameStartsWithControlChar(int controlChar) {
        ChannelBuffer responseBuffer = ChannelBuffers.dynamicBuffer();
        responseBuffer.writeBytes("HTTP/1.1 200 OK\r\nHost: netty.io\r\n".getBytes(CharsetUtil.US_ASCII));
        responseBuffer.writeByte(controlChar);
        responseBuffer.writeBytes("Transfer-Encoding: chunked\r\n\r\n".getBytes(CharsetUtil.US_ASCII));
        testInvalidResponseHeaders0(responseBuffer);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar1c() {
        testResponseHeaderNameEndsWithControlChar(0x1c);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar1d() {
        testResponseHeaderNameEndsWithControlChar(0x1d);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar1e() {
        testResponseHeaderNameEndsWithControlChar(0x1e);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar1f() {
        testResponseHeaderNameEndsWithControlChar(0x1f);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar0c() {
        testResponseHeaderNameEndsWithControlChar(0x0c);
    }

    private static void testResponseHeaderNameEndsWithControlChar(int controlChar) {
        ChannelBuffer responseBuffer = ChannelBuffers.dynamicBuffer();
        responseBuffer.writeBytes("HTTP/1.1 200 OK\r\nHost: netty.io\r\n".getBytes(CharsetUtil.US_ASCII));
        responseBuffer.writeBytes("Transfer-Encoding".getBytes(CharsetUtil.US_ASCII));
        responseBuffer.writeByte(controlChar);
        responseBuffer.writeBytes(": chunked\r\n\r\n".getBytes(CharsetUtil.US_ASCII));
        testInvalidResponseHeaders0(responseBuffer);
    }

    // ---------------------------------------------------------------------
    // CVE-2019-16869: no whitespace is allowed between a header field-name and
    // the colon (RFC 7230 section 3.2.4). For requests this must be rejected
    // (400 Bad Request); for responses the whitespace is silently skipped so
    // that interop with non-compliant servers is preserved.
    // Backport of netty/netty@39cafcb (issue #9571 / PR #9585).
    // ---------------------------------------------------------------------

    @Test
    public void testWhitespaceInRequestHeaderName() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Transfer-Encoding : chunked\r\n" +
                "Host: netty.io\r\n\r\n";
        testInvalidRequestHeaders0(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII));
    }

    @Test
    public void testWhitespaceInResponseHeaderName() {
        String responseStr = "HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding : chunked\r\n" +
                "Host: netty.io\r\n\r\n";
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpResponseDecoder());
        embedder.offer(ChannelBuffers.copiedBuffer(responseStr, CharsetUtil.US_ASCII));
        HttpResponse response = (HttpResponse) embedder.poll();
        assertNotNull(response);
        assertEquals(HttpHeaders.Values.CHUNKED,
                response.getHeader(HttpHeaders.Names.TRANSFER_ENCODING));
        assertEquals("netty.io", response.getHeader(HttpHeaders.Names.HOST));
    }

    private static void testInvalidHeaders0(String requestStr) {
        testInvalidRequestHeaders0(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII));
    }

    private static void testInvalidRequestHeaders0(ChannelBuffer requestBuffer) {
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        try {
            embedder.offer(requestBuffer);
            fail("Expected CodecEmbedderException");
        } catch (CodecEmbedderException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    private static void testInvalidResponseHeaders0(ChannelBuffer responseBuffer) {
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpResponseDecoder());
        try {
            embedder.offer(responseBuffer);
            fail("Expected CodecEmbedderException");
        } catch (CodecEmbedderException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }
}
