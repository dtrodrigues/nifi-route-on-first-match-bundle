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

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PropertyUpdates } from '../route-listing/route-order';

export interface ProcessorDetails {
    id: string;
    name: string;
    state: string;
    revision: {
        version: number;
        clientId: string | null;
    };
    properties: Record<string, string | null>;
    validationErrors: string[] | null;
}

export interface SaveRequest {
    processorId: string;
    revision: number;
    clientId: string;
    disconnectedNodeAcknowledged: boolean;
}

@Injectable({ providedIn: 'root' })
export class RouteOnFirstMatchService {
    private httpClient = inject(HttpClient);

    private static readonly API: string = 'api';

    getProcessorDetails(processorId: string): Observable<ProcessorDetails> {
        const params = new HttpParams().set('processorId', processorId);
        return this.httpClient.get<ProcessorDetails>(`${RouteOnFirstMatchService.API}/processor/details`, { params });
    }

    saveProperties(request: SaveRequest, properties: PropertyUpdates): Observable<ProcessorDetails> {
        const params = new HttpParams()
            .set('processorId', request.processorId)
            .set('revisionId', request.revision)
            .set('clientId', request.clientId)
            .set('disconnectedNodeAcknowledged', request.disconnectedNodeAcknowledged);

        return this.httpClient.put<ProcessorDetails>(
            `${RouteOnFirstMatchService.API}/processor/properties`,
            properties,
            {
                params
            }
        );
    }
}
