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
    deleteRouteUpdates,
    EVALUATION_ORDER,
    parseEvaluationOrder,
    reorderUpdates,
    Route,
    saveRouteUpdates,
    toRoutes
} from './route-order';

describe('route-order', () => {
    const urgent: Route = { name: 'urgent', expression: "${priority:equals('urgent')}" };
    const large: Route = { name: 'large', expression: '${size:gt(100)}' };

    it('should parse the evaluation order, ignoring blank lines and whitespace', () => {
        expect(parseEvaluationOrder(' b \n\n a\r\n')).toEqual(['b', 'a']);
        expect(parseEvaluationOrder(null)).toEqual([]);
    });

    it('should order listed routes first, then the rest alphabetically', () => {
        const routes = toRoutes({
            [EVALUATION_ORDER]: 'c\nmissing\na',
            a: 'A',
            b: 'B',
            c: 'C',
            d: 'D'
        });
        expect(routes.map((route) => route.name)).toEqual(['c', 'a', 'b', 'd']);
        expect(routes[0].expression).toEqual('C');
    });

    it('should save the new order when reordering', () => {
        expect(reorderUpdates([large, urgent])).toEqual({ [EVALUATION_ORDER]: 'large\nurgent' });
    });

    it('should append a new route', () => {
        expect(saveRouteUpdates([urgent], large, null)).toEqual({
            large: large.expression,
            [EVALUATION_ORDER]: 'urgent\nlarge'
        });
    });

    it('should rename a route in place, removing the old property', () => {
        const renamed: Route = { name: 'critical', expression: urgent.expression };
        expect(saveRouteUpdates([urgent, large], renamed, 'urgent')).toEqual({
            urgent: null,
            critical: urgent.expression,
            [EVALUATION_ORDER]: 'critical\nlarge'
        });
    });

    it('should delete a route and clear the order when none remain', () => {
        expect(deleteRouteUpdates([urgent, large], 'urgent')).toEqual({
            urgent: null,
            [EVALUATION_ORDER]: 'large'
        });
        expect(deleteRouteUpdates([urgent], 'urgent')).toEqual({ urgent: null, [EVALUATION_ORDER]: null });
    });
});
