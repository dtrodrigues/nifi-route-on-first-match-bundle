/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.nifi.web.ComponentDetails;
import org.apache.nifi.web.NiFiWebConfigurationContext;
import org.apache.nifi.web.NiFiWebConfigurationRequestContext;

import java.util.Map;

@Path("/processor")
public class ProcessorResource extends AbstractRouteResource {

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("/details")
    public Response getDetails(@QueryParam("processorId") final String processorId) {
        final NiFiWebConfigurationContext nifiWebContext = getWebConfigurationContext();
        final ComponentDetails componentDetails = ProcessorWebUtils.getComponentDetails(nifiWebContext, processorId, request);
        final Response.ResponseBuilder response = ProcessorWebUtils.applyCacheControl(Response.ok(componentDetails));
        return response.build();
    }

    @PUT
    @Produces({MediaType.APPLICATION_JSON})
    @Consumes({MediaType.APPLICATION_JSON})
    @Path("/configuration")
    public Response setConfiguration(@QueryParam("processorId") final String processorId, @QueryParam("revisionId") final Long revisionId,
                                     @QueryParam("clientId") final String clientId, @QueryParam("disconnectedNodeAcknowledged") final Boolean isDisconnectionAcknowledged,
                                     final ConfigurationUpdate update) {

        final NiFiWebConfigurationContext nifiWebContext = getWebConfigurationContext();
        final NiFiWebConfigurationRequestContext niFiRequestContext = ProcessorWebUtils.getRequestContext(processorId, revisionId, clientId, isDisconnectionAcknowledged, request);
        // The order (annotation data) and the routes (properties) are saved together, so a rename and its new place in the order apply at once
        final ComponentDetails componentDetails = nifiWebContext.updateComponent(niFiRequestContext, update.getAnnotationData(), update.getProperties());
        final Response.ResponseBuilder response = ProcessorWebUtils.applyCacheControl(Response.ok(componentDetails));
        return response.build();
    }

    public static class ConfigurationUpdate {

        private String annotationData;
        private Map<String, String> properties;

        /**
         * @return the evaluation order, or null to leave it unchanged
         */
        public String getAnnotationData() {
            return annotationData;
        }

        public void setAnnotationData(final String annotationData) {
            this.annotationData = annotationData;
        }

        /**
         * @return the properties to change; a null value removes the property
         */
        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(final Map<String, String> properties) {
            this.properties = properties;
        }
    }

}
