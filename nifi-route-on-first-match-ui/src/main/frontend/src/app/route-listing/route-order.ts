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

/**
 * Field of the annotation data that holds the evaluation order. Annotation data is not shown in the configuration
 * dialog, so the order can only be seen and changed here. Must match EvaluationOrder.FIELD.
 */
const ORDER_FIELD = 'evaluationOrder';

/** Relationship that receives FlowFiles matching no route. Must match RouteOnFirstMatch.REL_NO_MATCH. */
export const UNMATCHED = 'unmatched';

export interface Route {
    name: string;
    expression: string;
}

/** Property changes to send to the processor. A null value removes the property. */
export type PropertyUpdates = Record<string, string | null>;

/** A change to save: the evaluation order, which is kept in the annotation data, and any route properties. */
export interface ConfigurationUpdate {
    annotationData: string;
    properties: PropertyUpdates;
}

/**
 * Reads the evaluation order from the annotation data. Anything unreadable is treated as no order; the processor
 * reports it as invalid, and the next save from this UI replaces it.
 */
export function parseAnnotationData(annotationData: string | null | undefined): string[] {
    if (!annotationData || annotationData.trim().length === 0) {
        return [];
    }
    try {
        const order = JSON.parse(annotationData)?.[ORDER_FIELD];
        return Array.isArray(order) ? order.filter((name): name is string => typeof name === 'string') : [];
    } catch (_e) {
        return [];
    }
}

/**
 * Builds the routes in evaluation order the same way the processor does: routes in the saved order first,
 * then any remaining routes, such as those added in the configuration dialog, alphabetically.
 */
export function toRoutes(
    properties: Record<string, string | null>,
    annotationData: string | null | undefined
): Route[] {
    const routes = new Map<string, Route>();
    // every property is a route, and routes always have an expression
    Object.entries(properties).forEach(([name, expression]) =>
        routes.set(name, { name, expression: expression as string })
    );

    const ordered: Route[] = [];
    parseAnnotationData(annotationData).forEach((name) => {
        const route = routes.get(name);
        if (route) {
            ordered.push(route);
            routes.delete(name);
        }
    });

    const remaining = Array.from(routes.values()).sort((a, b) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0));
    return [...ordered, ...remaining];
}

export type RouteNameError = 'required' | 'reserved' | 'duplicate';

/**
 * Checks a route name as entered, before it is trimmed. Line breaks need no check, because the single-line name field
 * cannot contain them. Must agree with RouteOnFirstMatch.customValidate.
 */
export function routeNameError(input: string, routes: Route[], originalName: string | null): RouteNameError | null {
    const name = input.trim();
    if (name.length === 0) {
        return 'required';
    }
    if (name === UNMATCHED) {
        return 'reserved';
    }
    if (name !== originalName && routes.some((route) => route.name === name)) {
        return 'duplicate';
    }
    return null;
}

/** Saves the order of the given routes along with any route property changes. */
export function reorderUpdate(routes: Route[], properties: PropertyUpdates = {}): ConfigurationUpdate {
    return {
        annotationData: JSON.stringify({ [ORDER_FIELD]: routes.map((route) => route.name) }),
        properties
    };
}

/**
 * Adds a route at the end of the list, or replaces the route currently named originalName in place.
 */
export function saveRouteUpdate(routes: Route[], route: Route, originalName: string | null): ConfigurationUpdate {
    const properties: PropertyUpdates = {};
    let updated: Route[];

    if (originalName === null) {
        updated = [...routes, route];
    } else {
        updated = routes.map((existing) => (existing.name === originalName ? route : existing));
        if (originalName !== route.name) {
            properties[originalName] = null;
        }
    }

    properties[route.name] = route.expression;
    return reorderUpdate(updated, properties);
}

export function deleteRouteUpdate(routes: Route[], name: string): ConfigurationUpdate {
    return reorderUpdate(
        routes.filter((route) => route.name !== name),
        { [name]: null }
    );
}
