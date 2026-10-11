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

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.apache.nifi.web.InvalidRevisionException;
import org.apache.nifi.web.ResourceNotFoundException;

/**
 * Returns NiFi's reason for a failed request as plain text, so the Advanced UI can show it. Without this, NiFi refusing
 * a change, for example because the processor is scheduled to run, reaches the UI as an empty server error.
 */
@Provider
public class ConfigurationExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(final Exception exception) {
        if (exception instanceof WebApplicationException) {
            return ((WebApplicationException) exception).getResponse();
        }

        final Response.Status status;
        final String message;
        if (exception instanceof InvalidRevisionException) {
            status = Response.Status.CONFLICT;
            message = "The processor was changed elsewhere. Reload this window to see the latest configuration.";
        } else if (exception instanceof ResourceNotFoundException) {
            status = Response.Status.NOT_FOUND;
            message = "The processor no longer exists.";
        } else if (exception instanceof IllegalStateException || exception.getClass().getSimpleName().equals("ValidationException")) {
            // NiFi refuses configuration changes while the processor is running or waiting to run
            status = Response.Status.CONFLICT;
            // NiFi's ValidationException only provides its text through getLocalizedMessage()
            final String reason = exception.getLocalizedMessage();
            message = reason != null && reason.contains("running") ? reason + ". Stop the processor, then try again." : reason;
        } else {
            status = Response.Status.INTERNAL_SERVER_ERROR;
            message = exception.getLocalizedMessage() != null ? exception.getLocalizedMessage() : exception.getClass().getSimpleName();
        }

        return Response.status(status).type(MediaType.TEXT_PLAIN_TYPE).entity(message).build();
    }
}
