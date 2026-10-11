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
package io.github.dtrodrigues.nifi.route;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Reads the routes that the Advanced UI stores in the processor's annotation data, in evaluation order, in the form
 * {@code {"routes": [{"name": "urgent", "expression": "${priority:equals('urgent')}"}]}}. The routes are not
 * properties, so they can only be seen and changed in the Advanced UI.
 */
final class RouteDefinitions {

    private static final String ROUTES = "routes";
    private static final String NAME = "name";
    private static final String EXPRESSION = "expression";

    // Rejects anything after the JSON object, as the Advanced UI's JSON.parse does, so both read the same routes
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private RouteDefinitions() {
    }

    /**
     * A route as saved by the Advanced UI. Its name becomes a relationship, and its expression decides whether a
     * FlowFile is routed there.
     */
    static final class Route {
        private final String name;
        private final String expression;

        Route(final String name, final String expression) {
            this.name = name;
            this.expression = expression;
        }

        String name() {
            return name;
        }

        String expression() {
            return expression;
        }

        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof Route)) {
                return false;
            }
            final Route route = (Route) other;
            return name.equals(route.name) && expression.equals(route.expression);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, expression);
        }

        @Override
        public String toString() {
            return name + "=" + expression;
        }
    }

    /**
     * @return the routes in evaluation order; empty if none have been saved
     * @throws IllegalArgumentException if the annotation data is not in the expected form
     */
    static List<Route> fromAnnotationData(final String annotationData) {
        if (annotationData == null || annotationData.trim().isEmpty()) {
            return Collections.emptyList();
        }

        final JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(annotationData);
        } catch (final JsonProcessingException e) {
            throw new IllegalArgumentException("the routes saved by the Advanced UI are not valid JSON", e);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("the routes saved by the Advanced UI are not a JSON object");
        }

        final JsonNode routes = root.get(ROUTES);
        if (routes == null || routes.isNull()) {
            return Collections.emptyList();
        }
        if (!routes.isArray()) {
            throw new IllegalArgumentException(String.format("'%s' saved by the Advanced UI is not a list", ROUTES));
        }

        final List<Route> definitions = new ArrayList<>();
        for (final JsonNode route : routes) {
            final JsonNode name = route.get(NAME);
            final JsonNode expression = route.get(EXPRESSION);
            if (!route.isObject() || name == null || !name.isTextual() || expression == null || !expression.isTextual()) {
                throw new IllegalArgumentException(String.format("'%s' saved by the Advanced UI contains a route without a name and an expression", ROUTES));
            }
            definitions.add(new Route(name.asText(), expression.asText()));
        }
        return Collections.unmodifiableList(definitions);
    }
}
