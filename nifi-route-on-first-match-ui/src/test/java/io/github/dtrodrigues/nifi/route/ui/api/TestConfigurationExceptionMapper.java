/*
 * Copyright 2026 Dustin Rodrigues
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.dtrodrigues.nifi.route.ui.api;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.apache.nifi.web.InvalidRevisionException;
import org.apache.nifi.web.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestConfigurationExceptionMapper {

    private final ConfigurationExceptionMapper mapper = new ConfigurationExceptionMapper();

    @Test
    void testProcessorScheduledToRun() {
        final Response response = mapper.toResponse(new IllegalStateException(
                "1 validation error: Cannot modify configuration of RouteOnFirstMatch[id=1] while the Processor is running"));
        assertEquals(409, response.getStatus());
        assertEquals("1 validation error: Cannot modify configuration of RouteOnFirstMatch[id=1] while the Processor is running. "
                + "Stop the processor, then try again.", response.getEntity());
    }

    /** Like NiFi's ValidationException, which has no message and builds its text in getLocalizedMessage() */
    private static final class ValidationException extends RuntimeException {
        @Override
        public String getLocalizedMessage() {
            return "1 validation error: Cannot modify configuration of RouteOnFirstMatch[id=1] while the Processor is running";
        }
    }

    @Test
    void testNiFiValidationException() {
        final Response response = mapper.toResponse(new ValidationException());
        assertEquals(409, response.getStatus());
        assertEquals("1 validation error: Cannot modify configuration of RouteOnFirstMatch[id=1] while the Processor is running. "
                + "Stop the processor, then try again.", response.getEntity());
    }

    @Test
    void testStaleRevision() {
        final Response response = mapper.toResponse(new InvalidRevisionException("stale"));
        assertEquals(409, response.getStatus());
        assertEquals("The processor was changed elsewhere. Reload this window to see the latest configuration.", response.getEntity());
    }

    @Test
    void testProcessorRemoved() {
        assertEquals(404, mapper.toResponse(new ResourceNotFoundException("gone")).getStatus());
    }

    @Test
    void testUnexpectedError() {
        final Response response = mapper.toResponse(new RuntimeException("boom"));
        assertEquals(500, response.getStatus());
        assertEquals("boom", response.getEntity());
    }

    @Test
    void testWebApplicationExceptionKeepsItsResponse() {
        assertEquals(404, mapper.toResponse(new NotFoundException()).getStatus());
    }
}
