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

import org.apache.nifi.annotation.behavior.DefaultRunDuration;
import org.apache.nifi.annotation.behavior.DynamicRelationship;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.PropertyValue;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.Validator;
import org.apache.nifi.expression.AttributeExpression.ResultType;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Routes each FlowFile to exactly one relationship: the one belonging to the first route whose expression evaluates to true.
 * The routes, in evaluation order, are set in the processor's Advanced UI and stored in its annotation data, which the
 * configuration dialog does not show. Each route's name is also the name of a relationship.
 */
@SideEffectFree
@SupportsBatching(defaultDuration = DefaultRunDuration.TWENTY_FIVE_MILLIS)
@InputRequirement(Requirement.INPUT_REQUIRED)
@Tags({"attributes", "routing", "first", "match", "priority", "ordered", "Expression Language"})
@CapabilityDescription("Routes FlowFiles to the first route whose Expression Language condition evaluates to true. Routes are added, "
        + "edited and ordered by drag and drop in the Advanced UI, the only place they can be seen or changed; each route's name becomes "
        + "a relationship. A FlowFile is never cloned: it is routed to exactly one relationship, or to 'unmatched' if no condition "
        + "evaluates to true.")
@DynamicRelationship(name = "Name of a route", description = "FlowFiles for which this route, set in the Advanced UI, is the first to match")
@WritesAttributes({
    @WritesAttribute(attribute = RouteOnFirstMatch.ROUTE_ATTRIBUTE_KEY, description = "The relationship to which the FlowFile was routed")
})
@SeeAlso(classNames = {"org.apache.nifi.processors.standard.RouteOnAttribute"})
public class RouteOnFirstMatch extends AbstractProcessor {

    public static final String ROUTE_ATTRIBUTE_KEY = "RouteOnFirstMatch.Route";

    public static final Relationship REL_NO_MATCH = new Relationship.Builder()
            .name("unmatched")
            .description("FlowFiles that do not match any route will be routed here")
            .build();

    private static final Validator BOOLEAN_EXPRESSION = StandardValidators.createAttributeExpressionLanguageValidator(ResultType.BOOLEAN, false);

    /**
     * Routes are not properties, so a property added in the configuration dialog is always invalid
     */
    private static final Validator NOT_A_ROUTE = (subject, input, context) -> new ValidationResult.Builder()
            .subject(subject)
            .input(input)
            .valid(false)
            .explanation("routes are added in the Advanced UI, not as properties; remove this property and add the route there")
            .build();

    /**
     * The relationships as of the last validation: one for each saved route, and 'unmatched'. NiFi gives a processor its
     * annotation data only through a context, and revalidates it whenever the annotation data changes, so this is how the
     * relationships keep up with the routes saved in the Advanced UI.
     */
    private volatile Set<Relationship> relationships = Collections.singleton(REL_NO_MATCH);

    /**
     * Routes in evaluation order, built in {@link #onScheduled(ProcessContext)} for quick access in
     * {@link #onTrigger(ProcessContext, ProcessSession)}
     */
    private volatile List<Route> routes = Collections.emptyList();

    @Override
    public Set<Relationship> getRelationships() {
        return relationships;
    }

    @Override
    protected PropertyDescriptor getSupportedDynamicPropertyDescriptor(final String propertyDescriptorName) {
        return new PropertyDescriptor.Builder()
                .name(propertyDescriptorName)
                .addValidator(NOT_A_ROUTE)
                .dynamic(true)
                .build();
    }

    @Override
    protected Collection<ValidationResult> customValidate(final ValidationContext validationContext) {
        final List<RouteDefinitions.Route> definitions;
        try {
            definitions = RouteDefinitions.fromAnnotationData(validationContext.getAnnotationData());
        } catch (final IllegalArgumentException e) {
            // Keep the relationships, so that connections to them stay in place until the routes are saved again
            return Collections.singletonList(new ValidationResult.Builder()
                    .subject("Routes")
                    .valid(false)
                    .explanation(e.getMessage() + "; open Advanced and save the routes again to replace them")
                    .build());
        }

        final Set<Relationship> newRelationships = new HashSet<>();
        newRelationships.add(REL_NO_MATCH);
        for (final RouteDefinitions.Route definition : definitions) {
            newRelationships.add(new Relationship.Builder().name(definition.name()).build());
        }
        relationships = Collections.unmodifiableSet(newRelationships);

        final List<ValidationResult> results = new ArrayList<>();
        final Set<String> names = new HashSet<>();
        for (final RouteDefinitions.Route definition : definitions) {
            final String name = definition.name();
            final String subject = "Route '" + name + "'";
            final String problem;
            if (name.isEmpty()) {
                problem = "route names cannot be empty";
            } else if (name.equals(REL_NO_MATCH.getName())) {
                problem = String.format("'%s' is reserved for FlowFiles that match no route", REL_NO_MATCH.getName());
            } else if (!names.add(name)) {
                problem = "another route has the same name";
            } else if (name.contains("\n") || name.contains("\r")) {
                // Route names become relationship names, which are shown on a single line
                problem = "route names cannot contain line breaks";
            } else if (hasSurroundingWhitespace(name)) {
                problem = "route names cannot start or end with whitespace";
            } else {
                problem = null;
            }
            if (problem != null) {
                results.add(new ValidationResult.Builder().subject(subject).valid(false).explanation(problem).build());
            }

            final ValidationResult expressionResult = BOOLEAN_EXPRESSION.validate(subject, definition.expression(), validationContext);
            if (!expressionResult.isValid()) {
                results.add(expressionResult);
            }
        }

        return results;
    }

    /**
     * When this processor is scheduled, compile the routes in evaluation order for quick access during each onTrigger call
     * @param context ProcessContext used to retrieve the routes
     */
    @OnScheduled
    public void onScheduled(final ProcessContext context) {
        final Set<String> names = new LinkedHashSet<>();
        final List<Route> newRoutes = new ArrayList<>();
        for (final RouteDefinitions.Route definition : RouteDefinitions.fromAnnotationData(context.getAnnotationData())) {
            // Validation rejects duplicate names, so this only guards against evaluating a name twice
            if (names.add(definition.name())) {
                final Relationship relationship = new Relationship.Builder().name(definition.name()).build();
                newRoutes.add(new Route(relationship, context.newPropertyValue(definition.expression())));
            }
        }

        this.routes = Collections.unmodifiableList(newRoutes);
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) {
        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }

        Relationship destination = REL_NO_MATCH;
        for (final Route route : routes) {
            if (route.condition.evaluateAttributeExpressions(flowFile).asBoolean()) {
                destination = route.relationship;
                break;
            }
        }

        getLogger().debug("Routing {} to {}", flowFile, destination);
        flowFile = session.putAttribute(flowFile, ROUTE_ATTRIBUTE_KEY, destination.getName());
        session.getProvenanceReporter().route(flowFile, destination);
        session.transfer(flowFile, destination);
    }

    /**
     * Matches {@code !name.equals(name.strip())}, which needs Java 11
     */
    private static boolean hasSurroundingWhitespace(final String name) {
        return !name.isEmpty() && (Character.isWhitespace(name.codePointAt(0)) || Character.isWhitespace(name.codePointBefore(name.length())));
    }

    private static final class Route {
        private final Relationship relationship;
        private final PropertyValue condition;

        private Route(final Relationship relationship, final PropertyValue condition) {
            this.relationship = relationship;
            this.condition = condition;
        }
    }
}
