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

import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.MockProcessContext;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRouteOnFirstMatch {

    private static final String URGENT = "${priority:equals('urgent')}";
    private static final String LARGE = "${size:gt(100)}";

    private TestRunner runner;

    @BeforeEach
    void setRunner() {
        runner = TestRunners.newTestRunner(RouteOnFirstMatch.class);
    }

    /**
     * Saves the routes the way the Advanced UI does, in the annotation data
     * @param namesAndExpressions alternating route names and expressions, in evaluation order
     */
    private void setRoutes(final String... namesAndExpressions) {
        final List<String> routes = new ArrayList<>();
        for (int i = 0; i < namesAndExpressions.length; i += 2) {
            routes.add("{\"name\": \"%s\", \"expression\": \"%s\"}".formatted(namesAndExpressions[i], namesAndExpressions[i + 1]));
        }
        runner.setAnnotationData("{\"routes\": [%s]}".formatted(String.join(", ", routes)));
    }

    private List<String> invalidExplanations() {
        return ((MockProcessContext) runner.getProcessContext()).validate().stream()
                .filter(result -> !result.isValid())
                .map(ValidationResult::getExplanation)
                .toList();
    }

    private Set<String> relationshipNames() {
        return runner.getProcessor().getRelationships().stream().map(Relationship::getName).collect(Collectors.toSet());
    }

    @Test
    void testRoutesToFirstMatchInEvaluationOrder() {
        setRoutes("urgent", URGENT, "large", LARGE);

        runner.enqueue(new byte[0], Map.of("priority", "urgent", "size", "500"));
        runner.enqueue(new byte[0], Map.of("priority", "normal", "size", "500"));
        runner.enqueue(new byte[0], Map.of("priority", "normal", "size", "5"));
        runner.run(3);

        runner.assertTransferCount("urgent", 1);
        runner.assertTransferCount("large", 1);
        runner.assertTransferCount(RouteOnFirstMatch.REL_NO_MATCH, 1);

        final MockFlowFile urgent = runner.getFlowFilesForRelationship("urgent").getFirst();
        urgent.assertAttributeEquals("size", "500");
        urgent.assertAttributeEquals(RouteOnFirstMatch.ROUTE_ATTRIBUTE_KEY, "urgent");

        runner.getFlowFilesForRelationship("large").getFirst().assertAttributeEquals("priority", "normal");
        runner.getFlowFilesForRelationship(RouteOnFirstMatch.REL_NO_MATCH).getFirst()
                .assertAttributeEquals(RouteOnFirstMatch.ROUTE_ATTRIBUTE_KEY, "unmatched");
    }

    @Test
    void testReversedEvaluationOrder() {
        setRoutes("large", LARGE, "urgent", URGENT);

        runner.enqueue(new byte[0], Map.of("priority", "urgent", "size", "500"));
        runner.run();

        runner.assertAllFlowFilesTransferred("large", 1);
    }

    @Test
    void testLaterConditionsNotEvaluatedAfterFirstMatch() {
        // evaluating the second route would fail the run by dividing by zero
        setRoutes("urgent", URGENT, "large", "${size:toNumber():divide(0):gt(100)}");

        runner.enqueue(new byte[0], Map.of("priority", "urgent", "size", "500"));
        runner.run();

        runner.assertAllFlowFilesTransferred("urgent", 1);
    }

    @Test
    void testNoRoutesSendsEverythingToUnmatched() {
        runner.assertValid();
        assertEquals(Set.of("unmatched"), relationshipNames());

        runner.enqueue(new byte[0]);
        runner.run();

        runner.assertAllFlowFilesTransferred(RouteOnFirstMatch.REL_NO_MATCH, 1);
    }

    @Test
    void testRelationshipsFollowSavedRoutes() {
        setRoutes("urgent", URGENT, "large", LARGE);
        runner.assertValid();
        assertEquals(Set.of("urgent", "large", "unmatched"), relationshipNames());

        setRoutes("urgent", URGENT);
        runner.assertValid();
        assertEquals(Set.of("urgent", "unmatched"), relationshipNames());
    }

    @Test
    void testRelationshipsKeptWhenRoutesUnreadable() {
        setRoutes("urgent", URGENT);
        runner.assertValid();

        runner.setAnnotationData("not json");
        runner.assertNotValid();
        assertEquals(Set.of("urgent", "unmatched"), relationshipNames());
    }

    @Test
    void testInvalidWhenAnnotationDataIsMalformed() {
        for (final String annotationData : List.of("not json", "{\"routes\": []} garbage", "[]", "{\"routes\": {}}", "{\"routes\": [\"urgent\"]}",
                "{\"routes\": [{\"name\": \"urgent\"}]}", "{\"routes\": [{\"name\": 1, \"expression\": \"${a}\"}]}")) {
            runner.setAnnotationData(annotationData);
            runner.assertNotValid();
        }
    }

    @Test
    void testRouteDefinitionsFromAnnotationData() {
        assertEquals(List.of(), RouteDefinitions.fromAnnotationData(null));
        assertEquals(List.of(), RouteDefinitions.fromAnnotationData(" "));
        assertEquals(List.of(), RouteDefinitions.fromAnnotationData("{}"));
        assertEquals(List.of(new RouteDefinitions.Route("b", "${b}"), new RouteDefinitions.Route("a", "${a}")),
                RouteDefinitions.fromAnnotationData("{\"routes\": [{\"name\": \"b\", \"expression\": \"${b}\"}, {\"name\": \"a\", \"expression\": \"${a}\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> RouteDefinitions.fromAnnotationData("[]"));
    }

    @Test
    void testInvalidWhenExpressionIsNotBoolean() {
        setRoutes("large", "${size}");
        runner.assertNotValid();

        setRoutes("large", "${size:gt(100)");
        runner.assertNotValid();

        setRoutes("large", "");
        runner.assertNotValid();
    }

    @Test
    void testInvalidWhenRouteNamedUnmatched() {
        setRoutes("unmatched", LARGE);
        assertEquals(List.of("'unmatched' is reserved for FlowFiles that match no route"), invalidExplanations());
    }

    @Test
    void testInvalidWhenRouteNamesRepeat() {
        setRoutes("large", LARGE, "large", URGENT);
        assertEquals(List.of("another route has the same name"), invalidExplanations());
    }

    @Test
    void testInvalidWhenRouteNameIsEmptyOrHasLineBreak() {
        setRoutes("", LARGE);
        runner.assertNotValid();

        setRoutes("very\\nlarge", LARGE);
        runner.assertNotValid();
    }

    @Test
    void testInvalidWhenRouteNameHasSurroundingWhitespace() {
        setRoutes(" small", LARGE);
        runner.assertNotValid();
    }

    @Test
    void testPropertiesAreNotRoutes() {
        runner.setProperty("urgent", URGENT);
        final List<String> explanations = invalidExplanations();
        assertEquals(1, explanations.size());
        assertTrue(explanations.getFirst().contains("routes are added in the Advanced UI"));
        assertEquals(Set.of("unmatched"), relationshipNames());
    }
}
