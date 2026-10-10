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

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.util.MockProcessContext;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRouteOnFirstMatch {

    private TestRunner runner;

    @BeforeEach
    void setRunner() {
        runner = TestRunners.newTestRunner(RouteOnFirstMatch.class);
        runner.setProperty("urgent", "${priority:equals('urgent')}");
        runner.setProperty("large", "${size:gt(100)}");
    }

    /**
     * Sets the order the way the Advanced UI does, in the annotation data
     */
    private void setOrder(final String... names) {
        final String quoted = String.join(", ", Arrays.stream(names).map("\"%s\""::formatted).toList());
        runner.setAnnotationData("{\"evaluationOrder\": [%s]}".formatted(quoted));
    }

    @Test
    void testRoutesToFirstMatchInEvaluationOrder() {
        setOrder("urgent", "large");

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
        setOrder("large", "urgent");

        runner.enqueue(new byte[0], Map.of("priority", "urgent", "size", "500"));
        runner.run();

        runner.assertAllFlowFilesTransferred("large", 1);
    }

    @Test
    void testInvalidUntilEveryRouteIsPlaced() {
        runner.assertNotValid();

        setOrder("urgent");
        runner.assertNotValid();

        setOrder("urgent", "large");
        runner.assertValid();

        runner.setProperty("big", "${size:gt(10)}");
        runner.assertNotValid();
    }

    @Test
    void testUnplacedRoutesNamedInValidationMessage() {
        runner.setProperty("big", "${size:gt(10)}");
        setOrder("urgent");

        final List<String> explanations = ((MockProcessContext) runner.getProcessContext()).validate().stream()
                .filter(result -> !result.isValid())
                .map(ValidationResult::getExplanation)
                .toList();
        assertEquals(List.of("routes 'big', 'large' have not been placed in the order; open Advanced to place them"), explanations);
    }

    @Test
    void testSingleRouteNeedsNoOrder() {
        runner.removeProperty("large");
        runner.assertValid();

        runner.enqueue(new byte[0], Map.of("priority", "urgent"));
        runner.run();

        runner.assertAllFlowFilesTransferred("urgent", 1);
    }

    @Test
    void testOrderedRoutesThatNoLongerExistAreIgnored() {
        setOrder("removed", "large", "urgent");
        runner.assertValid();

        runner.enqueue(new byte[0], Map.of("priority", "urgent", "size", "500"));
        runner.run();

        runner.assertAllFlowFilesTransferred("large", 1);
    }

    @Test
    void testInvalidWhenAnnotationDataIsMalformed() {
        setOnlyRoute("small");
        for (final String annotationData : List.of("not json", "{\"evaluationOrder\": [\"urgent\"]} garbage", "[\"urgent\"]", "{\"evaluationOrder\": \"urgent\"}", "{\"evaluationOrder\": [1]}")) {
            runner.setAnnotationData(annotationData);
            runner.assertNotValid();
        }
    }

    @Test
    void testEvaluationOrderFromAnnotationData() {
        assertEquals(List.of(), EvaluationOrder.fromAnnotationData(null));
        assertEquals(List.of(), EvaluationOrder.fromAnnotationData(" "));
        assertEquals(List.of(), EvaluationOrder.fromAnnotationData("{}"));
        assertEquals(List.of("b", "a"), EvaluationOrder.fromAnnotationData("{\"evaluationOrder\": [\"b\", \"a\"]}"));
    }

    /**
     * Leaves only the given route, so the order is not what makes the processor invalid
     */
    private void setOnlyRoute(final String name) {
        runner.removeProperty("urgent");
        runner.removeProperty("large");
        runner.setProperty(name, "${size:gt(100)}");
    }

    @Test
    void testInvalidWhenConditionNamedUnmatched() {
        setOnlyRoute("unmatched");
        runner.assertNotValid();
    }

    @Test
    void testInvalidWhenConditionNameContainsLineBreak() {
        setOnlyRoute("very\nlarge");
        runner.assertNotValid();

        runner.removeProperty("very\nlarge");
        runner.setProperty("very\rlarge", "${size:gt(100)}");
        runner.assertNotValid();
    }

    @Test
    void testInvalidWhenConditionNameHasSurroundingWhitespace() {
        setOnlyRoute(" small");
        runner.assertNotValid();
    }

    @Test
    void testRoutesDeclaredInEvaluationOrder() {
        runner.setProperty("big", "${size:gt(10)}");
        setOrder("urgent", "large");
        runner.assertNotValid();
        assertEquals(List.of("urgent", "large", "big"), declaredRouteNames());

        setOrder("large", "big", "urgent");
        runner.assertValid();
        assertEquals(List.of("large", "big", "urgent"), declaredRouteNames());

        runner.removeProperty("big");
        assertEquals(List.of("large", "urgent"), declaredRouteNames());
        assertTrue(runner.getProcessor().getPropertyDescriptors().stream().allMatch(PropertyDescriptor::isDynamic));
    }

    @Test
    void testDeclaredRoutesStillValidated() {
        setOrder("urgent", "large");
        runner.assertValid();

        runner.setProperty("large", "${size:gt(100)");
        runner.assertNotValid();
    }

    private List<String> declaredRouteNames() {
        return runner.getProcessor().getPropertyDescriptors().stream().map(PropertyDescriptor::getName).toList();
    }

    @Test
    void testRelationships() {
        final Set<String> relationshipNames = runner.getProcessor().getRelationships().stream()
                .map(Relationship::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("urgent", "large", "unmatched"), relationshipNames);

        runner.removeProperty("large");
        final Set<String> remaining = runner.getProcessor().getRelationships().stream()
                .map(Relationship::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("urgent", "unmatched"), remaining);
    }
}
