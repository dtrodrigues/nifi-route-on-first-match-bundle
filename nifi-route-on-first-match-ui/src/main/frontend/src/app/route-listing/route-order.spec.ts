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
    isDisplayedOrderChanged,
    EVALUATION_ORDER,
    parseAnnotationData,
    reorderUpdate,
    Route,
    routeNameError,
    saveRouteUpdate,
    toRoutes
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
                [EVALUATION_ORDER]: 'ignored\nbecause it only displays the order',
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

    it('should save the new order when reordering, and display it in the property', () => {
        expect(reorderUpdate([large, urgent])).toEqual({
            annotationData: order('large', 'urgent'),
            properties: { [EVALUATION_ORDER]: 'large\nurgent' }
        });
    });

    it('should append a new route', () => {
        expect(saveRouteUpdate([urgent], large, null)).toEqual({
            annotationData: order('urgent', 'large'),
            properties: { large: large.expression, [EVALUATION_ORDER]: 'urgent\nlarge' }
        });
    });

    it('should rename a route in place, removing the old property', () => {
        const renamed: Route = { name: 'critical', expression: urgent.expression };
        expect(saveRouteUpdate([urgent, large], renamed, 'urgent')).toEqual({
            annotationData: order('critical', 'large'),
            properties: { urgent: null, critical: urgent.expression, [EVALUATION_ORDER]: 'critical\nlarge' }
        });
    });

    it('should delete a route and clear the displayed order when none remain', () => {
        expect(deleteRouteUpdate([urgent, large], 'urgent')).toEqual({
            annotationData: order('large'),
            properties: { urgent: null, [EVALUATION_ORDER]: 'large' }
        });
        expect(deleteRouteUpdate([urgent], 'urgent')).toEqual({
            annotationData: order(),
            properties: { urgent: null, [EVALUATION_ORDER]: null }
        });
    });
    it('should detect when the displayed order was edited outside the Advanced UI', () => {
        expect(isDisplayedOrderChanged({ [EVALUATION_ORDER]: ' urgent \n\nlarge' }, order('urgent', 'large'))).toBe(
            false
        );
        expect(isDisplayedOrderChanged({}, null)).toBe(false);
        expect(isDisplayedOrderChanged({ [EVALUATION_ORDER]: 'large\nurgent' }, order('urgent', 'large'))).toBe(true);
        expect(isDisplayedOrderChanged({}, order('urgent'))).toBe(true);
        expect(isDisplayedOrderChanged({ [EVALUATION_ORDER]: 'urgent' }, null)).toBe(true);
    });

    it('should reject route names that cannot be listed in the evaluation order', () => {
        const routes = [urgent, large];
        expect(routeNameError('small', routes, null)).toBeNull();
        expect(routeNameError(' small ', routes, null)).toBeNull();
        expect(routeNameError('   ', routes, null)).toEqual('required');
        expect(routeNameError('very\nlarge', routes, null)).toEqual('lineBreak');
        expect(routeNameError('very\rlarge', routes, null)).toEqual('lineBreak');
        expect(routeNameError('unmatched', routes, null)).toEqual('reserved');
        expect(routeNameError(EVALUATION_ORDER, routes, null)).toEqual('reserved');
        expect(routeNameError('large', routes, null)).toEqual('duplicate');
        expect(routeNameError('large', routes, 'large')).toBeNull();
        expect(routeNameError('large', routes, 'urgent')).toEqual('duplicate');
    });
});
