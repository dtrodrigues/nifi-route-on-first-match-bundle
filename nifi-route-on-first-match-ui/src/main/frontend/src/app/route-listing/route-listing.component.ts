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

import { Component, computed, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Observable } from 'rxjs';
import { ProcessorDetails, RouteOnFirstMatchService } from '../service/route-on-first-match.service';
import {
    deleteRouteUpdates,
    EVALUATION_ORDER,
    PropertyUpdates,
    reorderUpdates,
    Route,
    saveRouteUpdates,
    toRoutes,
    UNMATCHED
} from './route-order';

/** Marks the panel for a route that has not been saved yet. */
const NEW_ROUTE = '\u0000new';

@Component({
    selector: 'route-listing',
    templateUrl: './route-listing.component.html',
    styleUrls: ['./route-listing.component.scss'],
    imports: [
        NgTemplateOutlet,
        ReactiveFormsModule,
        CdkDropList,
        CdkDrag,
        CdkDragHandle,
        MatButtonModule,
        MatExpansionModule,
        MatFormFieldModule,
        MatInputModule,
        MatMenuModule,
        MatProgressSpinnerModule
    ]
})
export class RouteListing {
    private service = inject(RouteOnFirstMatchService);
    private formBuilder = inject(FormBuilder);

    // NiFi passes these as query parameters, which QueryStringToFragmentFilter moves into the URL fragment
    private params = new URLSearchParams(window.location.hash.split('?')[1] ?? '');
    private processorId = this.params.get('id') ?? '';
    private revision = Number(this.params.get('revision') ?? 0);
    private clientId = this.params.get('clientId') ?? '';
    private disconnectedNodeAcknowledged = this.params.get('disconnectedNodeAcknowledged') === 'true';
    readonly editable = this.params.get('editable') === 'true';

    protected readonly NEW_ROUTE = NEW_ROUTE;
    protected readonly UNMATCHED = UNMATCHED;
    protected readonly expressionHint = "Must return true or false, e.g. ${priority:equals('urgent')}";

    loading = signal(true);
    saving = signal(false);
    loadError = signal<string | null>(null);
    saveError = signal<string | null>(null);
    processorName = signal('');
    validationErrors = signal<string[]>([]);
    routes = signal<Route[]>([]);
    editing = signal<string | null>(null);

    reorderDisabled = computed(() => !this.editable || this.saving() || this.editing() !== null);

    routeForm = this.formBuilder.nonNullable.group({
        name: ['', [Validators.required, (control: AbstractControl) => this.validateName(control)]],
        expression: ['', Validators.required]
    });

    constructor() {
        this.load();
    }

    load(): void {
        this.loading.set(true);
        this.loadError.set(null);
        this.service.getProcessorDetails(this.processorId).subscribe({
            next: (details) => {
                this.applyDetails(details);
                this.loading.set(false);
            },
            error: (error: HttpErrorResponse) => {
                this.loadError.set(this.errorMessage(error));
                this.loading.set(false);
            }
        });
    }

    drop(event: CdkDragDrop<Route[]>): void {
        if (event.previousIndex === event.currentIndex) {
            return;
        }

        const reordered = [...this.routes()];
        moveItemInArray(reordered, event.previousIndex, event.currentIndex);
        this.routes.set(reordered);
        this.save(reorderUpdates(reordered));
    }

    startEdit(route: Route): void {
        this.editing.set(route.name);
        this.routeForm.reset({ name: route.name, expression: route.expression });
        this.setFormEnabled();
    }

    startNew(): void {
        this.editing.set(NEW_ROUTE);
        this.routeForm.reset({ name: '', expression: '' });
        this.setFormEnabled();
    }

    panelClosed(name: string): void {
        // another panel may already have been opened, in which case it owns the edit
        if (this.editing() === name) {
            this.editing.set(null);
        }
    }

    cancelEdit(): void {
        this.editing.set(null);
    }

    saveRoute(): void {
        const editing = this.editing();
        if (editing === null || this.routeForm.invalid) {
            return;
        }

        const route: Route = {
            name: this.routeForm.getRawValue().name.trim(),
            expression: this.routeForm.getRawValue().expression
        };
        const originalName = editing === NEW_ROUTE ? null : editing;
        this.save(saveRouteUpdates(this.routes(), route, originalName), () => this.editing.set(null));
    }

    deleteRoute(route: Route): void {
        this.save(deleteRouteUpdates(this.routes(), route.name), () => {
            if (this.editing() === route.name) {
                this.editing.set(null);
            }
        });
    }

    private save(updates: PropertyUpdates, onSuccess?: () => void): void {
        this.saving.set(true);
        this.saveError.set(null);
        this.setFormEnabled();

        const request$: Observable<ProcessorDetails> = this.service.saveProperties(
            {
                processorId: this.processorId,
                revision: this.revision,
                clientId: this.clientId,
                disconnectedNodeAcknowledged: this.disconnectedNodeAcknowledged
            },
            updates
        );

        request$.subscribe({
            next: (details) => {
                this.applyDetails(details);
                this.saving.set(false);
                this.setFormEnabled();
                onSuccess?.();
            },
            error: (error: HttpErrorResponse) => {
                this.saveError.set(this.errorMessage(error));
                this.saving.set(false);
                this.setFormEnabled();
                // the list may have been changed optimistically, so show what the processor actually has
                this.load();
            }
        });
    }

    private applyDetails(details: ProcessorDetails): void {
        this.revision = details.revision.version;
        if (details.revision.clientId) {
            this.clientId = details.revision.clientId;
        }
        this.processorName.set(details.name);
        this.routes.set(toRoutes(details.properties ?? {}));
        this.validationErrors.set(details.validationErrors ?? []);
    }

    private setFormEnabled(): void {
        if (this.editable && !this.saving()) {
            this.routeForm.enable();
        } else {
            this.routeForm.disable();
        }
    }

    private validateName(control: AbstractControl): ValidationErrors | null {
        const name = (control.value as string).trim();
        if (name === UNMATCHED || name === EVALUATION_ORDER) {
            return { reserved: true };
        }
        const editing = this.editing();
        if (name !== editing && this.routes().some((route) => route.name === name)) {
            return { duplicate: true };
        }
        return null;
    }

    private errorMessage(error: HttpErrorResponse): string {
        if (typeof error.error === 'string' && error.error.length > 0) {
            return error.error;
        }
        return error.message;
    }
}
