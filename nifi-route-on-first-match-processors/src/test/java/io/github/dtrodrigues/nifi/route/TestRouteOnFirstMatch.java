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

import org.apache.nifi.processor.Relationship;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestRouteOnFirstMatch {

    private TestRunner runner;

    @BeforeEach
    void setRunner() {
        runner = TestRunners.newTestRunner(RouteOnFirstMatch.class);
        runner.setProperty("urgent", "${priority:equals('urgent')}");
        runner.setProperty("large", "${size:gt(100)}");
    }

    @Test
    void testRoutesToFirstMatchInEvaluationOrder() {
        runner.setProperty(RouteOnFirstMatch.EVALUATION_ORDER, "urgent\nlarge");

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
        runner.setProperty(RouteOnFirstMatch.EVALUATION_ORDER, "large\nurgent");

        runner.enqueue(new byte[0], Map.of("priority", "urgent", "size", "500"));
        runner.run();

        runner.assertAllFlowFilesTransferred("large", 1);
    }

    @Test
    void testUnlistedConditionsEvaluatedAlphabeticallyAfterListed() {
        runner.setProperty("big", "${size:gt(10)}");
        runner.setProperty(RouteOnFirstMatch.EVALUATION_ORDER, "urgent");

        runner.enqueue(new byte[0], Map.of("priority", "normal", "size", "500"));
        runner.run();

        runner.assertAllFlowFilesTransferred("big", 1);
    }

    @Test
    void testInvalidWhenEvaluationOrderNamesUnknownCondition() {
        runner.setProperty(RouteOnFirstMatch.EVALUATION_ORDER, "urgent\nmissing");
        runner.assertNotValid();
    }

    @Test
    void testInvalidWhenEvaluationOrderHasDuplicates() {
        runner.setProperty(RouteOnFirstMatch.EVALUATION_ORDER, "urgent\nlarge\nurgent");
        runner.assertNotValid();
    }

    @Test
    void testInvalidWhenConditionNamedUnmatched() {
        runner.setProperty("unmatched", "${size:gt(100)}");
        runner.assertNotValid();
    }

    @Test
    void testInvalidWhenConditionNameContainsLineBreak() {
        runner.assertValid();

        runner.setProperty("very\nlarge", "${size:gt(1000)}");
        runner.assertNotValid();

        runner.removeProperty("very\nlarge");
        runner.setProperty("very\rlarge", "${size:gt(1000)}");
        runner.assertNotValid();
    }

    @Test
    void testInvalidWhenConditionNameHasSurroundingWhitespace() {
        runner.setProperty(" small", "${size:lt(10)}");
        runner.assertNotValid();
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

    @Test
    void testResolveEvaluationOrder() {
        assertEquals(List.of("c", "a", "b", "d"),
                RouteOnFirstMatch.resolveEvaluationOrder(" c \n\nmissing\na\n", Set.of("d", "b", "a", "c")));
        assertEquals(List.of("a", "b"), RouteOnFirstMatch.resolveEvaluationOrder(null, Set.of("b", "a")));
    }
}
