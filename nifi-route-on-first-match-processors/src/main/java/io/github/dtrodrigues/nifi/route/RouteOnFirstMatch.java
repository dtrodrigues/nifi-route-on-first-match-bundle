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
import org.apache.nifi.annotation.behavior.DynamicProperty;
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
import org.apache.nifi.annotation.documentation.UseCase;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.PropertyValue;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.expression.AttributeExpression.ResultType;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Routes each FlowFile to exactly one relationship: the one belonging to the first user-defined expression that evaluates to true.
 * The evaluation order is set by drag and drop in the processor's Advanced UI and stored in the processor's annotation data,
 * which the configuration dialog does not show, so the order cannot be changed there.
 */
@SideEffectFree
@SupportsBatching(defaultDuration = DefaultRunDuration.TWENTY_FIVE_MILLIS)
@InputRequirement(Requirement.INPUT_REQUIRED)
@Tags({"attributes", "routing", "first", "match", "priority", "ordered", "Expression Language"})
@CapabilityDescription("Routes FlowFiles to the first relationship whose Expression Language condition evaluates to true. Conditions are evaluated "
        + "in the order set by drag and drop in the Advanced UI, the only place the order can be seen or changed. A FlowFile is never "
        + "cloned: it is routed to exactly one relationship, or to 'unmatched' if no condition evaluates to true.")
@DynamicProperty(name = "Relationship Name", value = "Expression Language expression that returns a boolean value indicating whether or not the FlowFile matches",
        expressionLanguageScope = ExpressionLanguageScope.FLOWFILE_ATTRIBUTES,
        description = "Routes FlowFiles to the Relationship specified in the Dynamic Property Key if the expression is the first, in evaluation order, "
                + "to evaluate to true")
@DynamicRelationship(name = "Name from Dynamic Property", description = "FlowFiles for which this Dynamic Property is the first to match")
@WritesAttributes({
    @WritesAttribute(attribute = RouteOnFirstMatch.ROUTE_ATTRIBUTE_KEY, description = "The relationship to which the FlowFile was routed")
})
@SeeAlso(classNames = {"org.apache.nifi.processors.standard.RouteOnAttribute"})
@UseCase(
    description = "Route data to exactly one relationship, chosen by the first of several prioritized conditions that its attributes satisfy.",
    keywords = {"attributes", "routing", "first", "priority", "ordered", "expression language"},
    configuration = """
        For each route that a FlowFile might be routed to, add a new property. The name of the property is the name of the relationship, \
        and the value is an Expression Language expression that returns a boolean value.

        Open the Advanced UI and drag the routes into the order in which they should be evaluated, highest priority first. \
        The order can only be seen and changed in the Advanced UI. Routes not yet placed there are evaluated last, alphabetically.

        For example:
            - "urgent" = "${priority:equals('urgent')}"
            - "large" = "${fileSize:gt(1000000)}"
            - In the Advanced UI, "urgent" is dragged above "large"

        A FlowFile that is urgent and large is routed only to `urgent`. A FlowFile that matches neither condition is routed to `unmatched`.
        """
)
public class RouteOnFirstMatch extends AbstractProcessor {

    public static final String ROUTE_ATTRIBUTE_KEY = "RouteOnFirstMatch.Route";

    public static final Relationship REL_NO_MATCH = new Relationship.Builder()
            .name("unmatched")
            .description("FlowFiles that do not match any user-defined expression will be routed here")
            .build();

    private final AtomicReference<Set<Relationship>> relationships = new AtomicReference<>(Set.of(REL_NO_MATCH));
    private volatile Set<String> dynamicPropertyNames = Set.of();

    /**
     * Conditions in evaluation order, built in {@link #onScheduled(ProcessContext)} for quick access in
     * {@link #onTrigger(ProcessContext, ProcessSession)}
     */
    private volatile List<Route> routes = List.of();

    @Override
    public Set<Relationship> getRelationships() {
        return relationships.get();
    }

    @Override
    protected PropertyDescriptor getSupportedDynamicPropertyDescriptor(final String propertyDescriptorName) {
        return new PropertyDescriptor.Builder()
                .required(false)
                .name(propertyDescriptorName)
                .addValidator(StandardValidators.createAttributeExpressionLanguageValidator(ResultType.BOOLEAN, false))
                .dynamic(true)
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .build();
    }

    @Override
    public void onPropertyModified(final PropertyDescriptor descriptor, final String oldValue, final String newValue) {
        if (!descriptor.isDynamic()) {
            return;
        }

        final Set<String> newDynamicPropertyNames = new HashSet<>(dynamicPropertyNames);
        if (newValue == null) {
            newDynamicPropertyNames.remove(descriptor.getName());
        } else if (oldValue == null) {
            newDynamicPropertyNames.add(descriptor.getName());
        }
        this.dynamicPropertyNames = Set.copyOf(newDynamicPropertyNames);

        final Set<Relationship> newRelationships = new HashSet<>();
        for (final String propertyName : newDynamicPropertyNames) {
            newRelationships.add(new Relationship.Builder().name(propertyName).build());
        }
        newRelationships.add(REL_NO_MATCH);
        this.relationships.set(Set.copyOf(newRelationships));
    }

    @Override
    protected Collection<ValidationResult> customValidate(final ValidationContext validationContext) {
        final List<ValidationResult> results = new ArrayList<>();

        final Set<String> conditionNames = new HashSet<>();
        for (final PropertyDescriptor descriptor : validationContext.getProperties().keySet()) {
            if (descriptor.isDynamic()) {
                conditionNames.add(descriptor.getName());
            }
        }

        if (conditionNames.contains(REL_NO_MATCH.getName())) {
            results.add(new ValidationResult.Builder()
                    .subject(REL_NO_MATCH.getName())
                    .valid(false)
                    .explanation("'%s' is reserved for FlowFiles that match no condition".formatted(REL_NO_MATCH.getName()))
                    .build());
        }

        // Route names become relationship names, which are shown on a single line and are easily mistyped with stray whitespace
        for (final String name : conditionNames) {
            if (name.contains("\n") || name.contains("\r")) {
                results.add(new ValidationResult.Builder()
                        .subject(name)
                        .valid(false)
                        .explanation("route names cannot contain line breaks")
                        .build());
            } else if (!name.equals(name.strip())) {
                results.add(new ValidationResult.Builder()
                        .subject(name)
                        .valid(false)
                        .explanation("route names cannot start or end with whitespace")
                        .build());
            }
        }

        try {
            EvaluationOrder.fromAnnotationData(validationContext.getAnnotationData());
        } catch (final IllegalArgumentException e) {
            results.add(new ValidationResult.Builder()
                    .subject("Evaluation Order")
                    .valid(false)
                    .explanation(e.getMessage() + "; reorder the routes in the Advanced UI to replace it")
                    .build());
        }

        return results;
    }

    /**
     * When this processor is scheduled, resolve the evaluation order of the dynamic properties
     * for quick access during each onTrigger call
     * @param context ProcessContext used to retrieve dynamic properties
     */
    @OnScheduled
    public void onScheduled(final ProcessContext context) {
        final Map<String, PropertyDescriptor> dynamicDescriptors = new HashMap<>();
        for (final PropertyDescriptor descriptor : context.getProperties().keySet()) {
            if (descriptor.isDynamic()) {
                dynamicDescriptors.put(descriptor.getName(), descriptor);
            }
        }

        final List<String> orderedNames = resolveEvaluationOrder(EvaluationOrder.fromAnnotationData(context.getAnnotationData()), dynamicDescriptors.keySet());
        final List<Route> newRoutes = new ArrayList<>(orderedNames.size());
        for (final String name : orderedNames) {
            final Relationship relationship = new Relationship.Builder().name(name).build();
            newRoutes.add(new Route(relationship, context.getProperty(dynamicDescriptors.get(name))));
        }

        this.routes = List.copyOf(newRoutes);
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) {
        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }

        Relationship destination = REL_NO_MATCH;
        for (final Route route : routes) {
            if (route.condition().evaluateAttributeExpressions(flowFile).asBoolean()) {
                destination = route.relationship();
                break;
            }
        }

        getLogger().debug("Routing {} to {}", flowFile, destination);
        flowFile = session.putAttribute(flowFile, ROUTE_ATTRIBUTE_KEY, destination.getName());
        session.getProvenanceReporter().route(flowFile, destination);
        session.transfer(flowFile, destination);
    }

    /**
     * Orders the given condition names: those in the evaluation order come first, in that order,
     * followed by any remaining names in alphabetical order. Names that are not conditions, such as routes
     * since removed in the configuration dialog, are ignored.
     */
    private static List<String> resolveEvaluationOrder(final List<String> evaluationOrder, final Set<String> conditionNames) {
        final Set<String> ordered = new LinkedHashSet<>();
        for (final String name : evaluationOrder) {
            if (conditionNames.contains(name)) {
                ordered.add(name);
            }
        }
        for (final String name : new TreeSet<>(conditionNames)) {
            ordered.add(name);
        }
        return List.copyOf(ordered);
    }

    private record Route(Relationship relationship, PropertyValue condition) {
    }
}
