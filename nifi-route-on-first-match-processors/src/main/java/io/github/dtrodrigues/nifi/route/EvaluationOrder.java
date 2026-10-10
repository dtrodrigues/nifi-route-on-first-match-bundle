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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the route order that the Advanced UI stores in the processor's annotation data, in the form
 * {@code {"evaluationOrder": ["urgent", "large"]}}. Annotation data is not shown in the processor's
 * configuration dialog, so the order can only be seen and changed in the Advanced UI.
 */
final class EvaluationOrder {

    private static final String FIELD = "evaluationOrder";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private EvaluationOrder() {
    }

    /**
     * @return the route names in evaluation order; empty if no order has been set
     * @throws IllegalArgumentException if the annotation data is not in the expected form
     */
    static List<String> fromAnnotationData(final String annotationData) {
        if (annotationData == null || annotationData.isBlank()) {
            return List.of();
        }

        final JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(annotationData);
        } catch (final JsonProcessingException e) {
            throw new IllegalArgumentException("the order saved by the Advanced UI is not valid JSON", e);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("the order saved by the Advanced UI is not a JSON object");
        }

        final JsonNode order = root.get(FIELD);
        if (order == null || order.isNull()) {
            return List.of();
        }
        if (!order.isArray()) {
            throw new IllegalArgumentException("'%s' saved by the Advanced UI is not a list".formatted(FIELD));
        }

        final List<String> names = new ArrayList<>();
        for (final JsonNode name : order) {
            if (!name.isTextual()) {
                throw new IllegalArgumentException("'%s' saved by the Advanced UI contains a value that is not a route name".formatted(FIELD));
            }
            names.add(name.asText());
        }
        return List.copyOf(names);
    }
}
