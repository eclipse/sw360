/*
 * Copyright Bosch Software Innovations GmbH, 2018.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.sw360.rest.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public abstract class Sw360CORSFilter implements Filter {

    @Value("${sw360.cors.allowed-origin:}")
    private String allowedOrigin;
    @Value("${sw360.cors.max-age:3600}")
    private String accessControlMaxAge;
    @Value("${sw360.cors.allow-credentials:true}")
    private String accessControlAllowCredentials;

    private static final String ALLOWED_HTTP_METHODS = allowedHttpMethods();
    private static final String ALLOWED_HTTP_HEADERS = allowedHttpHeaders();

    @Override
    public void init(FilterConfig filterConfig)  {}

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if(allowedOrigin != null) {
            HttpServletRequest httpServletRequest = (HttpServletRequest)servletRequest;
            HttpServletResponse httpServletResponse = (HttpServletResponse)servletResponse;
            setCORSHeader(httpServletResponse);
            if(httpServletRequest.getMethod().equalsIgnoreCase(HttpMethod.OPTIONS.name())) {
                httpServletResponse.setStatus(HttpServletResponse.SC_OK);
            } else {
                filterChain.doFilter(servletRequest, servletResponse);
            }
        } else {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    @Override
    public void destroy() {}

    private void setCORSHeader(HttpServletResponse httpServletResponse) {
        httpServletResponse.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigin);
        httpServletResponse.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, ALLOWED_HTTP_METHODS);
        httpServletResponse.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, accessControlMaxAge);
        httpServletResponse.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, ALLOWED_HTTP_HEADERS);
        httpServletResponse.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, accessControlAllowCredentials);
    }

    private static String allowedHttpMethods() {
        List<String> httpMethods = new ArrayList<>();
        httpMethods.add(HttpMethod.GET.name());
        httpMethods.add(HttpMethod.POST.name());
        httpMethods.add(HttpMethod.DELETE.name());
        httpMethods.add(HttpMethod.PATCH.name());
        return String.join(",", httpMethods);
    }

    private static String allowedHttpHeaders() {
        List<String> httpHeaders = new ArrayList<>();
        httpHeaders.add(HttpHeaders.CONTENT_TYPE);
        httpHeaders.add(HttpHeaders.AUTHORIZATION);
        return String.join(",", httpHeaders);
    }
}
