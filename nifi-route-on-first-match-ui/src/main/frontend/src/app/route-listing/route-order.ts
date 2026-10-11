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
 * Field of the annotation data that holds the routes, in evaluation order. Annotation data is not shown in the
 * configuration dialog, so the routes can only be seen and changed here. Must match RouteDefinitions.ROUTES.
 */
const ROUTES_FIELD = 'routes';

/** Relationship that receives FlowFiles matching no route. Must match RouteOnFirstMatch.REL_NO_MATCH. */
export const UNMATCHED = 'unmatched';

export interface Route {
    name: string;
    expression: string;
}

/** A change to save: all routes, in evaluation order, which are kept in the annotation data. */
export interface ConfigurationUpdate {
    annotationData: string;
}

function isRoute(value: unknown): value is Route {
    return (
        value !== null &&
        typeof value === 'object' &&
        typeof (value as Route).name === 'string' &&
        typeof (value as Route).expression === 'string'
    );
}

/**
 * Reads the routes from the annotation data, in evaluation order. Anything unreadable is treated as no routes; the
 * processor reports it as invalid, and the next save from this UI replaces it.
 */
export function toRoutes(annotationData: string | null | undefined): Route[] {
    if (!annotationData || annotationData.trim().length === 0) {
        return [];
    }
    try {
        const routes = JSON.parse(annotationData)?.[ROUTES_FIELD];
        return Array.isArray(routes)
            ? routes.filter(isRoute).map((route) => ({ name: route.name, expression: route.expression }))
            : [];
    } catch (_e) {
        return [];
    }
}

/**
 * Whether the annotation data holds something other than readable routes, which makes the processor invalid.
 * Must agree with RouteDefinitions.fromAnnotationData.
 */
export function areRoutesUnreadable(annotationData: string | null | undefined): boolean {
    if (!annotationData || annotationData.trim().length === 0) {
        return false;
    }
    try {
        const root = JSON.parse(annotationData);
        if (root === null || typeof root !== 'object' || Array.isArray(root)) {
            return true;
        }
        const routes = root[ROUTES_FIELD];
        if (routes === undefined || routes === null) {
            return false;
        }
        return !Array.isArray(routes) || !routes.every(isRoute);
    } catch (_e) {
        return true;
    }
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

/** Saves the given routes, in the order given. */
export function routesUpdate(routes: Route[]): ConfigurationUpdate {
    return {
        annotationData: JSON.stringify({
            [ROUTES_FIELD]: routes.map((route) => ({ name: route.name, expression: route.expression }))
        })
    };
}

/**
 * Adds a route at the end of the list, or replaces the route currently named originalName in place.
 */
export function saveRouteUpdate(routes: Route[], route: Route, originalName: string | null): ConfigurationUpdate {
    if (originalName === null) {
        return routesUpdate([...routes, route]);
    }
    return routesUpdate(routes.map((existing) => (existing.name === originalName ? route : existing)));
}

export function deleteRouteUpdate(routes: Route[], name: string): ConfigurationUpdate {
    return routesUpdate(routes.filter((route) => route.name !== name));
}
