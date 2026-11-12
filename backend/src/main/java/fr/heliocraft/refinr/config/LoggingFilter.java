package fr.heliocraft.refinr.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class LoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        MDC.put("path", httpRequest.getRequestURI());
        MDC.put("method", httpRequest.getMethod());

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}