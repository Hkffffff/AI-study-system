package com.aistudy.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;

class ErrorCodeTest {

    @Test
    void fromStatusPrefersGenericCodeForSharedStatus() {
        assertThat(ErrorCode.fromStatus(HttpStatusCode.valueOf(400))).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ErrorCode.fromStatus(HttpStatusCode.valueOf(404))).isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void fromStatusFallsBackByStatusClass() {
        assertThat(ErrorCode.fromStatus(HttpStatusCode.valueOf(418))).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ErrorCode.fromStatus(HttpStatusCode.valueOf(503))).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }
}
