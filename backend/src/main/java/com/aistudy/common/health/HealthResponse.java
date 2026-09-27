package com.aistudy.common.health;

import java.time.Instant;

/** {@code status}: UP / DEGRADED; {@code database}: UP / DOWN. */
record HealthResponse(String status, String database, Instant time) {
}
