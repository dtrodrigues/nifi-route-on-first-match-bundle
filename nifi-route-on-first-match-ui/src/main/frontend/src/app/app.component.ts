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

import { Component } from '@angular/core';
import { RouteListing } from './route-listing/route-listing.component';

const DARK_THEME = 'DARK_THEME';
const LIGHT_THEME = 'LIGHT_THEME';

/**
 * Reads the theme the user chose in NiFi. The custom UI is served from the same origin as NiFi,
 * so it shares NiFi's local storage, where the choice is kept as {"item": "DARK_THEME"}.
 */
function nifiThemeSetting(): string | null {
    try {
        const entry = localStorage.getItem('theme');
        return entry ? JSON.parse(entry).item : null;
    } catch (_e) {
        return null;
    }
}

function applyTheme(osPrefersDark: boolean): void {
    const theme = nifiThemeSetting();
    const dark = theme === DARK_THEME || (theme !== LIGHT_THEME && osPrefersDark);
    document.body.classList.toggle('darkMode', dark);
}

@Component({
    selector: 'route-on-first-match',
    template: '<route-listing></route-listing>',
    imports: [RouteListing]
})
export class AppComponent {
    constructor() {
        const darkModeQuery = window.matchMedia?.('(prefers-color-scheme: dark)');
        applyTheme(darkModeQuery?.matches ?? false);
        darkModeQuery?.addEventListener('change', (e) => applyTheme(e.matches));
    }
}
