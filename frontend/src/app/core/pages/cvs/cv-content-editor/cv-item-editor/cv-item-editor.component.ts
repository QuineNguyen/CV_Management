import { CdkDrag, CdkDragHandle } from "@angular/cdk/drag-drop";
import { ChangeDetectionStrategy, Component, HostListener, input, output, signal } from "@angular/core";
import { FormGroup, ReactiveFormsModule } from "@angular/forms";
import { MatToolbarModule } from "@angular/material/toolbar";
import { MatDatepickerModule } from "@angular/material/datepicker";
import { DateAdapter, MAT_DATE_FORMATS, MatNativeDateModule } from "@angular/material/core";
import { FieldDescriptor, FieldKind, notSpecifiedLabel, optionsFor, SectionDescriptor, SelectOption } from "../../../../models/cv-section-descriptor.model";
import { CvLanguage } from "../../../../enums/cv-language.enum";
import { CvDatePipe } from "../../../../utils/cv-date.util";
import { CustomDateAdapter, DD_MM_YYYY_FORMATS } from "../../../../utils/app-date-adapter.util";

/*
 * One entry of a REPEATED section, rendered from its section descriptor rather than a bespoke
 * template - seven near-identical templates would drift apart the first time a field is added.
 * 
 * - Collapsed by default so a CV with twenty entries is still navigable; the header shows the
 * two fields that identify the entry.
 */
@Component({
    selector: 'app-cv-item-editor',
    standalone: true,
    imports: [ReactiveFormsModule, CdkDrag, CdkDragHandle, MatToolbarModule, MatDatepickerModule, MatNativeDateModule, CvDatePipe],
    providers: [
        { provide: DateAdapter, useClass: CustomDateAdapter },
        { provide: MAT_DATE_FORMATS, useValue: DD_MM_YYYY_FORMATS },
    ],
    templateUrl: './cv-item-editor.component.html',
    styleUrl: './cv-item-editor.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvItemEditorComponent {

    readonly FieldKind = FieldKind;

    readonly section = input.required<SectionDescriptor>();
    readonly group = input.required<FormGroup>();
    readonly index = input.required<number>();
    readonly expanded = input(false);
    readonly readOnly = input(false);
    readonly language = input.required<CvLanguage>();

    readonly toggled = output<number>();
    readonly removed = output<number>();

    readonly openSelectKey = signal<string | null>(null);

    // Falls back to a positional label so a brand-new entry is still identifiable.
    title(): string {
        const key = this.section().titleField;
        const value = key ? this.group().get(key)?.value : null;
        return this.text(value) ?? `New ${this.section().itemNoun ?? 'entry'}`;
    }

    subtitle(): string | null {
        const key = this.section().subtitleField;
        if (!key) {
            return null;
        }
        const value = this.group().get(key)?.value;
        if (!value) {
            return null;
        }
        const field = this.section().fields.find(f => f.key === key);
        if (field?.kind === FieldKind.Select && field.optionSet) {
            const option = this.options(field).find(o => o.value === value);
            if (option) {
                return option.label;
            }
        }
        return this.text(value);
    }

    options(field: FieldDescriptor): readonly SelectOption[] {
        return field.optionSet ? optionsFor(field.optionSet, this.language()) : [];
    }

    emptyOptionLabel(): string {
        return notSpecifiedLabel(this.language());
    }

    // A placeholder from structure sync warns rather than blocks; the owner still has to fill it.
    get untranslated(): boolean {
        return this.group().get('is_untranslated')?.value === true;
    }

    get deletedInMaster(): boolean {
        return this.group().get('deleted_in_master')?.value === true;
    }

    hasError(field: FieldDescriptor, error: string): boolean {
        const control = this.group().get(field.key);
        return !!control && control.touched && control.hasError(error);
    }

    toggleSelect(fieldKey: string, event: MouseEvent): void {
        if (this.readOnly()) {
            return;
        }
        event.stopPropagation();
        this.openSelectKey.update(current => (current === fieldKey ? null : fieldKey));
    }

    selectOption(fieldKey: string, value: string | null): void {
        const control = this.group().get(fieldKey);
        if (control) {
            control.setValue(value);
            control.markAsDirty();
            control.markAsTouched();
        }
        this.openSelectKey.set(null);
    }

    getSelectedOptionLabel(field: FieldDescriptor): string {
        const value = this.group().get(field.key)?.value;
        if (!value) {
            return this.emptyOptionLabel();
        }
        const option = this.options(field).find(opt => opt.value === value);
        return option ? option.label : value;
    }

    @HostListener('document:click')
    closeSelects(): void {
        this.openSelectKey.set(null);
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        this.openSelectKey.set(null);
    }

    onToggle(): void {
        this.openSelectKey.set(null);
        this.toggled.emit(this.index());
    }

    onRemove(event: MouseEvent): void {
        this.openSelectKey.set(null);
        event.stopPropagation();
        this.removed.emit(this.index());
    }

    private text(value: unknown): string | null {
        if (value === null || value === undefined) {
            return null;
        }
        const asText = String(value).trim();
        return asText.length ? asText : null;
    }
}