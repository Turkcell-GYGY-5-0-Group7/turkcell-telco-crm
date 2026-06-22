package com.telco.template.application;

import com.telco.platform.cqrs.Query;

/**
 * Sample read-only query. Returns a static health-style payload to demonstrate the query path.
 */
public record PingQuery() implements Query<PingResponse> {
}
