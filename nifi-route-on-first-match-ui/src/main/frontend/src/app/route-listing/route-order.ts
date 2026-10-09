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
 * Name of the processor property that displays the evaluation order, one route per line. It cannot be used to change
 * the order: the processor is invalid unless it matches the annotation data. Must match RouteOnFirstMatch.EVALUATION_ORDER.
 */
export const EVALUATION_ORDER = 'Evaluation Order';

/** Field of the annotation data that holds the evaluation order. Must match EvaluationOrder.FIELD. */
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
 * Whether the Evaluation Order property no longer displays the saved order, because it was edited in the configuration
 * dialog. Compares the way the processor does, ignoring blank lines and surrounding whitespace.
 */
export function isDisplayedOrderChanged(
    properties: Record<string, string | null>,
    annotationData: string | null | undefined
): boolean {
    const displayed = (properties[EVALUATION_ORDER] ?? '')
        .split(/\r\n|\r|\n/)
        .map((line) => line.trim())
        .filter((line) => line.length > 0);
    const saved = parseAnnotationData(annotationData);
    return displayed.length !== saved.length || displayed.some((name, i) => name !== saved[i]);
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
    Object.entries(properties)
        .filter(([name, expression]) => name !== EVALUATION_ORDER && expression !== null)
        .forEach(([name, expression]) => routes.set(name, { name, expression: expression as string }));

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

export type RouteNameError = 'required' | 'lineBreak' | 'reserved' | 'duplicate';

/**
 * Checks a route name as entered, before it is trimmed. Names are stored one per line in the Evaluation Order,
 * so a name containing a line break could not be ordered. Must agree with RouteOnFirstMatch.customValidate.
 */
export function routeNameError(input: string, routes: Route[], originalName: string | null): RouteNameError | null {
    const name = input.trim();
    if (name.length === 0) {
        return 'required';
    }
    if (/[\r\n]/.test(name)) {
        return 'lineBreak';
    }
    if (name === UNMATCHED || name === EVALUATION_ORDER) {
        return 'reserved';
    }
    if (name !== originalName && routes.some((route) => route.name === name)) {
        return 'duplicate';
    }
    return null;
}

/** Saves the order of the given routes, and displays it in the Evaluation Order property. */
function withOrder(routes: Route[], properties: PropertyUpdates = {}): ConfigurationUpdate {
    const names = routes.map((route) => route.name);
    return {
        annotationData: JSON.stringify({ [ORDER_FIELD]: names }),
        properties: { ...properties, [EVALUATION_ORDER]: names.length > 0 ? names.join('\n') : null }
    };
}

export function reorderUpdate(routes: Route[]): ConfigurationUpdate {
    return withOrder(routes);
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
    return withOrder(updated, properties);
}

export function deleteRouteUpdate(routes: Route[], name: string): ConfigurationUpdate {
    return withOrder(
        routes.filter((route) => route.name !== name),
        { [name]: null }
    );
}
