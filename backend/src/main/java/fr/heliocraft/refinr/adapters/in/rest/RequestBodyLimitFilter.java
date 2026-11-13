// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Plafond de taille du corps des requetes, meme sans le proxy nginx du frontend ({@code client_max_body_size}) :
 * <ul>
 *   <li>{@code Content-Length} au-dela de la limite : {@code 413} sans lire le corps ;</li>
 *   <li>corps sans {@code Content-Length} ({@code Transfer-Encoding: chunked}) : lu au plus jusqu'a la limite + 1 octet,
 *       {@code 413} au-dela ; sinon le corps lu est rejoue tel quel a la suite de la chaine.</li>
 * </ul>
 * Le corps n'est jamais journalise : seulement la limite et la taille annoncee.
 */
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestBodyLimitFilter.class);

    private final long maxBodyBytes;
    private final ObjectMapper mapper;

    public RequestBodyLimitFilter(long maxBodyBytes, ObjectMapper mapper) {
        if (maxBodyBytes <= 0) {
            throw new IllegalArgumentException("The maximum body size must be positive: " + maxBodyBytes);
        }
        this.maxBodyBytes = maxBodyBytes;
        this.mapper = mapper;
    }

    public long maxBodyBytes() {
        return maxBodyBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long declared = request.getContentLengthLong();
        if (declared > maxBodyBytes) {
            reject(request, response, declared + " bytes declared");
            return;
        }
        if (declared < 0 && request.getHeader("Transfer-Encoding") != null) {
            byte[] body = readAtMost(request.getInputStream(), maxBodyBytes + 1);
            if (body.length > maxBodyBytes) {
                reject(request, response, "chunked body");
                return;
            }
            chain.doFilter(new BufferedBodyRequest(request, body), response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static byte[] readAtMost(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while (total < limit && (read = in.read(buffer, 0, (int) Math.min(buffer.length, limit - total))) != -1) {
            out.write(buffer, 0, read);
            total += read;
        }
        return out.toByteArray();
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String size) throws IOException {
        log.warn("Rejected {} {}: request body over {} bytes ({})", request.getMethod(), request.getRequestURI(),
                maxBodyBytes, size);
        response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // Le client peut encore envoyer le reste du corps : la connexion n'est pas reutilisee
        response.setHeader("Connection", "close");
        mapper.writeValue(response.getOutputStream(),
                ErrorResponses.error("The request body exceeds the maximum size of " + maxBodyBytes + " bytes"));
    }

    /** Requete dont le corps, deja lu et borne, est rejoue depuis la memoire. */
    private static final class BufferedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        BufferedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Asynchronous reads are not supported");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
