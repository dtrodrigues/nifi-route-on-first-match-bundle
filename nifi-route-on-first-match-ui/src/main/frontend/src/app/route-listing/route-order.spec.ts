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

import {
    areRoutesUnreadable,
    deleteRouteUpdate,
    Route,
    routeNameError,
    routesUpdate,
    saveRouteUpdate,
    toRoutes
} from './route-order';

describe('route-order', () => {
    const urgent: Route = { name: 'urgent', expression: "${priority:equals('urgent')}" };
    const large: Route = { name: 'large', expression: '${size:gt(100)}' };

    const saved = (...routes: Route[]) => JSON.stringify({ routes });

    it('should read the routes in order from the annotation data', () => {
        expect(toRoutes(saved(large, urgent))).toEqual([large, urgent]);
        expect(toRoutes(null)).toEqual([]);
        expect(toRoutes('{}')).toEqual([]);
        expect(toRoutes('not json')).toEqual([]);
        expect(toRoutes('{"routes": "a"}')).toEqual([]);
    });

    it('should detect routes the processor cannot read', () => {
        expect(areRoutesUnreadable(null)).toBe(false);
        expect(areRoutesUnreadable(' ')).toBe(false);
        expect(areRoutesUnreadable('{}')).toBe(false);
        expect(areRoutesUnreadable(saved(urgent))).toBe(false);
        expect(areRoutesUnreadable('not json')).toBe(true);
        expect(areRoutesUnreadable(saved(urgent) + ' garbage')).toBe(true);
        expect(areRoutesUnreadable('null')).toBe(true);
        expect(areRoutesUnreadable('[]')).toBe(true);
        expect(areRoutesUnreadable('{"routes": {}}')).toBe(true);
        expect(areRoutesUnreadable('{"routes": ["urgent"]}')).toBe(true);
        expect(areRoutesUnreadable('{"routes": [{"name": "urgent"}]}')).toBe(true);
    });

    it('should save the routes in the order given', () => {
        expect(routesUpdate([large, urgent])).toEqual({ annotationData: saved(large, urgent) });
    });

    it('should append a new route', () => {
        expect(saveRouteUpdate([urgent], large, null)).toEqual({ annotationData: saved(urgent, large) });
    });

    it('should rename a route in place', () => {
        const renamed: Route = { name: 'critical', expression: urgent.expression };
        expect(saveRouteUpdate([urgent, large], renamed, 'urgent')).toEqual({ annotationData: saved(renamed, large) });
    });

    it('should delete a route', () => {
        expect(deleteRouteUpdate([urgent, large], 'urgent')).toEqual({ annotationData: saved(large) });
        expect(deleteRouteUpdate([urgent], 'urgent')).toEqual({ annotationData: saved() });
    });

    it('should reject route names that cannot be used as relationships', () => {
        const routes = [urgent, large];
        expect(routeNameError('small', routes, null)).toBeNull();
        expect(routeNameError(' small ', routes, null)).toBeNull();
        expect(routeNameError('   ', routes, null)).toEqual('required');
        expect(routeNameError('unmatched', routes, null)).toEqual('reserved');
        expect(routeNameError('large', routes, null)).toEqual('duplicate');
        expect(routeNameError('large', routes, 'large')).toBeNull();
        expect(routeNameError('large', routes, 'urgent')).toEqual('duplicate');
    });
});
