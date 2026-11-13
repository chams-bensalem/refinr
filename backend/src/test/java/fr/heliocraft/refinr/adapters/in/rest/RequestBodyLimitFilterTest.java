// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Plafond du corps : sous la limite, au-dessus avec Content-Length, au-dessus en chunked (sans Content-Length). */
class RequestBodyLimitFilterTest {

    private static final int MAX = 100;
    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(MAX, new ObjectMapper());

    /** Requete chunked : pas de Content-Length (-1), corps lu en flux, compte des octets effectivement lus. */
    private static final class ChunkedRequest extends MockHttpServletRequest {
        private final ByteArrayInputStream body;

        ChunkedRequest(byte[] body) {
            super("POST", "/api/v1/analyze-preview");
            this.body = new ByteArrayInputStream(body);
            addHeader("Transfer-Encoding", "chunked");
            setContentType("application/json");
        }

        int unread() {
            return body.available();
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new ServletInputStream() {
                @Override public int read() { return body.read(); }
                @Override public int read(byte[] b, int off, int len) { return body.read(b, off, len); }
                @Override public boolean isFinished() { return body.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {}
            };
        }
    }

    private static MockHttpServletRequest withContentLength(int size) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/analyze-preview");
        request.setContent(new byte[size]);
        return request;
    }

    @Test
    @DisplayName("Under or at the limit (with Content-Length, or chunked): the request goes on with its body intact")
    void underTheLimitPasses() throws Exception {
        for (int size : new int[] {0, MAX - 1, MAX}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(withContentLength(size), response, chain);
            assertNotNull(chain.getRequest(), "size " + size);
            assertEquals(200, response.getStatus());
        }

        byte[] body = "{\"content\":\"é\"}".repeat(6).getBytes(StandardCharsets.UTF_8);
        assertTrue(body.length <= MAX);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new ChunkedRequest(body), new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest());
        assertArrayEquals(body, chain.getRequest().getInputStream().readAllBytes());
        assertEquals(body.length, chain.getRequest().getContentLengthLong());
        assertEquals(new String(body, StandardCharsets.UTF_8), chain.getRequest().getReader().readLine());

        byte[] exact = new byte[MAX];
        chain = new MockFilterChain();
        filter.doFilter(new ChunkedRequest(exact), new MockHttpServletResponse(), chain);
        assertEquals(MAX, chain.getRequest().getInputStream().readAllBytes().length);
    }

    @Test
    @DisplayName("Content-Length over the limit: 413 JSON without reading the body, the chain is not called")
    void contentLengthOverTheLimitIs413() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(withContentLength(MAX + 1), response, chain);

        assertNull(chain.getRequest());
        assertEquals(413, response.getStatus());
        assertEquals("close", response.getHeader("Connection"));
        assertTrue(response.getContentAsString().contains("\"success\":false"));
        assertTrue(response.getContentAsString().contains("The request body exceeds the maximum size of 100 bytes"));
    }

    @Test
    @DisplayName("Chunked body over the limit: read at most limit + 1 bytes, then 413, the chain is not called")
    void chunkedOverTheLimitIs413() throws Exception {
        ChunkedRequest request = new ChunkedRequest(new byte[10 * MAX]);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString().contains("The request body exceeds the maximum size of 100 bytes"));
        // Lecture bornee : le reste du corps n'est jamais lu
        assertEquals(10 * MAX - (MAX + 1), request.unread());
    }

    @Test
    @DisplayName("A non-positive limit is refused")
    void nonPositiveLimitIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new RequestBodyLimitFilter(0, new ObjectMapper()));
    }
}
