package com.ligitabl.api.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class RequestIdTest {

    @Test
    void keepsASafeIncomingId() {
        assertThat(RequestId.resolve("abc-123.DEF_4:5")).isEqualTo("abc-123.DEF_4:5");
    }

    @Test
    void replacesAMissingOrUnsafeIdWithAUuid() {
        for (String incoming : new String[] {null, "", "a\r\nX-Injected: 1", "<script>", "x".repeat(129)}) {
            String resolved = RequestId.resolve(incoming);
            assertThat(UUID.fromString(resolved)).as(incoming).isNotNull();
        }
    }
}
