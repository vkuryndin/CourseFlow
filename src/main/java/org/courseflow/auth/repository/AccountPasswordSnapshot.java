package org.courseflow.auth.repository;

/** Scalar credential snapshot: no managed entity or lazy association escapes a query. */
public record AccountPasswordSnapshot(Long accountId, String passwordHash) {
    @Override public String toString() { return "AccountPasswordSnapshot[redacted]"; }
}
