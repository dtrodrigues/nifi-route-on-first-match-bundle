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
    deleteRouteUpdate,
    parseAnnotationData,
    reorderUpdate,
    Route,
    routeNameError,
    saveRouteUpdate,
    toRoutes,
    unplacedRoutes
} from './route-order';

describe('route-order', () => {
    const urgent: Route = { name: 'urgent', expression: "${priority:equals('urgent')}" };
    const large: Route = { name: 'large', expression: '${size:gt(100)}' };

    const order = (...names: string[]) => JSON.stringify({ evaluationOrder: names });

    it('should read the evaluation order from the annotation data', () => {
        expect(parseAnnotationData(order('b', 'a'))).toEqual(['b', 'a']);
        expect(parseAnnotationData(null)).toEqual([]);
        expect(parseAnnotationData('{}')).toEqual([]);
        expect(parseAnnotationData('not json')).toEqual([]);
        expect(parseAnnotationData('{"evaluationOrder": "a"}')).toEqual([]);
    });

    it('should order saved routes first, then the rest alphabetically', () => {
        const routes = toRoutes(
            {
                a: 'A',
                b: 'B',
                c: 'C',
                d: 'D'
            },
            order('c', 'missing', 'a')
        );
        expect(routes.map((route) => route.name)).toEqual(['c', 'a', 'b', 'd']);
        expect(routes[0].expression).toEqual('C');
    });

    it('should find routes not yet placed, unless there is only one route', () => {
        expect(unplacedRoutes([urgent, large], order('urgent'))).toEqual(['large']);
        expect(unplacedRoutes([urgent, large], null)).toEqual(['urgent', 'large']);
        expect(unplacedRoutes([urgent, large], order('large', 'urgent'))).toEqual([]);
        expect(unplacedRoutes([urgent], null)).toEqual([]);
    });

    it('should save the new order when reordering', () => {
        expect(reorderUpdate([large, urgent])).toEqual({
            annotationData: order('large', 'urgent'),
            properties: {}
        });
    });

    it('should append a new route', () => {
        expect(saveRouteUpdate([urgent], large, null)).toEqual({
            annotationData: order('urgent', 'large'),
            properties: { large: large.expression }
        });
    });

    it('should rename a route in place, removing the old property', () => {
        const renamed: Route = { name: 'critical', expression: urgent.expression };
        expect(saveRouteUpdate([urgent, large], renamed, 'urgent')).toEqual({
            annotationData: order('critical', 'large'),
            properties: { urgent: null, critical: urgent.expression }
        });
    });

    it('should delete a route', () => {
        expect(deleteRouteUpdate([urgent, large], 'urgent')).toEqual({
            annotationData: order('large'),
            properties: { urgent: null }
        });
        expect(deleteRouteUpdate([urgent], 'urgent')).toEqual({
            annotationData: order(),
            properties: { urgent: null }
        });
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
