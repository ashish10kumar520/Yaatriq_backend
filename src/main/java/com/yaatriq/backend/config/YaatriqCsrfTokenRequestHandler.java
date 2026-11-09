package com.yaatriq.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

public class YaatriqCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {
    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String[] headerNames = {
                csrfToken.getHeaderName(),
                "X-CSRF-TOKEN",
                "X-CSRFToken"
        };
        for (String headerName : headerNames) {
            String token = request.getHeader(headerName);
            if (token != null && !token.isBlank()) {
                return token;
            }
        }
        return request.getParameter(csrfToken.getParameterName());
    }
}
