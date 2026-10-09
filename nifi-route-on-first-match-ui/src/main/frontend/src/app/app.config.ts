/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { ApplicationConfig } from '@angular/core';
import { provideAnimations, provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient, withXsrfConfiguration } from '@angular/common/http';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';

const entry = localStorage.getItem('disable-animations');
let disableAnimations: string = entry !== null ? JSON.parse(entry).item : '';

// honor OS settings if user has not explicitly disabled animations for the application
if (disableAnimations !== 'true' && disableAnimations !== 'false') {
    disableAnimations = window.matchMedia('(prefers-reduced-motion: reduce)').matches.toString();
}

export const appConfig: ApplicationConfig = {
    providers: [
        disableAnimations === 'true' ? provideNoopAnimations() : provideAnimations(),
        { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline' } },
        provideHttpClient(
            withXsrfConfiguration({
                cookieName: '__Secure-Request-Token',
                headerName: 'Request-Token'
            })
        )
    ]
};
