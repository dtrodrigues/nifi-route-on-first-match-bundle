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
 * Name of the processor property that holds the route names, one per line, in evaluation order.
 * Must match RouteOnFirstMatch.EVALUATION_ORDER.
 */
export const EVALUATION_ORDER = 'Evaluation Order';

/** Relationship that receives FlowFiles matching no route. Must match RouteOnFirstMatch.REL_NO_MATCH. */
export const UNMATCHED = 'unmatched';

export interface Route {
    name: string;
    expression: string;
}

/** Property changes to send to the processor. A null value removes the property. */
export type PropertyUpdates = Record<string, string | null>;

export function parseEvaluationOrder(value: string | null | undefined): string[] {
    if (!value) {
        return [];
    }
    return value
        .split(/\r\n|\r|\n/)
        .map((line) => line.trim())
        .filter((line) => line.length > 0);
}

/**
 * Builds the routes in evaluation order from the processor properties, the same way the processor does:
 * names listed in the Evaluation Order first, in that order, then any remaining routes alphabetically.
 */
export function toRoutes(properties: Record<string, string | null>): Route[] {
    const routes = new Map<string, Route>();
    Object.entries(properties)
        .filter(([name, expression]) => name !== EVALUATION_ORDER && expression !== null)
        .forEach(([name, expression]) => routes.set(name, { name, expression: expression as string }));

    const ordered: Route[] = [];
    parseEvaluationOrder(properties[EVALUATION_ORDER]).forEach((name) => {
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

function evaluationOrder(routes: Route[]): PropertyUpdates {
    return { [EVALUATION_ORDER]: routes.length > 0 ? routes.map((route) => route.name).join('\n') : null };
}

export function reorderUpdates(routes: Route[]): PropertyUpdates {
    return evaluationOrder(routes);
}

/**
 * Adds a route at the end of the list, or replaces the route currently named originalName in place.
 */
export function saveRouteUpdates(routes: Route[], route: Route, originalName: string | null): PropertyUpdates {
    const updates: PropertyUpdates = {};
    let updated: Route[];

    if (originalName === null) {
        updated = [...routes, route];
    } else {
        updated = routes.map((existing) => (existing.name === originalName ? route : existing));
        if (originalName !== route.name) {
            updates[originalName] = null;
        }
    }

    updates[route.name] = route.expression;
    return { ...updates, ...evaluationOrder(updated) };
}

export function deleteRouteUpdates(routes: Route[], name: string): PropertyUpdates {
    return {
        [name]: null,
        ...evaluationOrder(routes.filter((route) => route.name !== name))
    };
}
