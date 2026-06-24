package com.telco.gateway.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wraps an incoming request so filters can add or remove headers before the gateway
 * proxies the call downstream. Used by JwtClaimsFilter and CorrelationIdFilter.
 */
public final class MutableHttpServletRequest extends HttpServletRequestWrapper {

    private final Map<String, String> addedHeaders = new HashMap<>();
    private final Set<String> removedHeaders = new HashSet<>();

    public MutableHttpServletRequest(HttpServletRequest request) {
        super(request);
    }

    public void putHeader(String name, String value) {
        addedHeaders.put(name.toLowerCase(), value);
        removedHeaders.remove(name.toLowerCase());
    }

    public void removeHeader(String name) {
        removedHeaders.add(name.toLowerCase());
        addedHeaders.remove(name.toLowerCase());
    }

    @Override
    public String getHeader(String name) {
        String lower = name.toLowerCase();
        if (removedHeaders.contains(lower)) {
            return null;
        }
        if (addedHeaders.containsKey(lower)) {
            return addedHeaders.get(lower);
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String lower = name.toLowerCase();
        if (removedHeaders.contains(lower)) {
            return Collections.emptyEnumeration();
        }
        if (addedHeaders.containsKey(lower)) {
            return Collections.enumeration(List.of(addedHeaders.get(lower)));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new HashSet<>(Collections.list(super.getHeaderNames()));
        names.removeAll(removedHeaders);
        names.addAll(addedHeaders.keySet());
        return Collections.enumeration(names);
    }
}
