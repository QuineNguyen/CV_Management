import { ChangeDetectionStrategy, Component, effect, inject, input, output } from "@angular/core";
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from "@angular/forms";
import { CvSectionEditorComponent } from "./cv-section-editor/cv-section-editor.component";
import { CV_SECTIONS, FieldKind, SectionDescriptor } from "../../../models/cv-section-descriptor.model";
import { CvContent, emptyCvContent } from "../../../models/cv-content.model";
import { CdkDragDrop, moveItemInArray } from "@angular/cdk/drag-drop";
import { CvLanguage } from "../../../enums/cv-language.enum";

/*
 * The full CV content form: two SINGLE sections as FormGroups, seven REPEATED ones as FormArrays.
 *
 * - Two conversations matter and both live here rather than in the pages:
 *      + Blank strings become null on the way out. An empty input and an absent value read the same
 *        to a person and letting them differ would fill the change log with edits nobody made.
 *      + display_order is renumbered from array position on save. The array order is the truth; a
 *        stored number that disagrees with what the user sees is the bug this avoids.
 * 
 * - item_id is generated on the client when an entry is added, but the server fills in anything
 * missing and rejects duplicates - this is a convenience, not the guarantee.
 */
@Component({
    selector: 'app-cv-content-editor',
    standalone: true,
    imports: [ReactiveFormsModule, CvSectionEditorComponent],
    templateUrl: './cv-content-editor.component.html',
    styleUrl: './cv-content-editor.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvContentEditorComponent {

    private readonly fb = inject(FormBuilder);

    readonly sections = CV_SECTIONS;

    readonly content = input<CvContent | null>(null);
    // True while a draft is under review: the content is frozen for everyone, owner included.
    readonly readOnly = input(false);

    readonly language = input.required<CvLanguage>();

    readonly dirtyChanged = output<boolean>();

    readonly form: FormGroup = this.buildForm();

    constructor() {
        effect(() => {
            this.patch(this.content() ?? emptyCvContent());
            if (this.readOnly()) {
                this.form.disable({ emitEvent: false });
            } else {
                this.form.enable({ emitEvent: false });
            }
        });

        this.form.valueChanges.subscribe(() => this.dirtyChanged.emit(this.form.dirty));
    }

    // ---------- Public API used by the pages ----------

    // True when every required field is filled; the pages disable their save button on false.
    get valid(): boolean {
        return this.form.valid;
    }

    get dirty(): boolean {
        return this.form.dirty;
    }

    markAllTouched(): void {
        this.form.markAllAsTouched();
    }

    // Builds the payload, normalising blanks and renumbering display_order from position.
    toContent(): CvContent {
        const result = emptyCvContent() as unknown as Record<string, unknown>;

        for (const section of this.sections) {
            if (section.repeated) {
                result[section.key] = this.arrayOf(section.key).controls
                    .map((group, index) => this.readItem(section, group, index));
            } else {
                result[section.key] = this.readSingle(section);
            }
        }
        return result as unknown as CvContent;
    }

    controlOf(section: SectionDescriptor): FormGroup | FormArray {
        return this.form.get(section.key) as FormGroup | FormArray;
    }

    // ---------- Item mutations ----------

    addItem(section: SectionDescriptor): void {
        this.arrayOf(section.key).push(this.buildItemGroup(section));
        this.form.markAsDirty();
    }

    removeItem(section: SectionDescriptor, index: number): void {
        this.arrayOf(section.key).removeAt(index);
        this.form.markAsDirty();
    }

    reorderItems(section: SectionDescriptor, event: CdkDragDrop<unknown>): void {
        if (event.previousIndex === event.currentIndex) {
            return;
        }
        const array = this.arrayOf(section.key);
        const controls = [...array.controls];

        moveItemInArray(controls, event.previousIndex, event.currentIndex);
        controls.forEach((control, index) => array.setControl(index, control, { emitEvent: false }));

        array.updateValueAndValidity();
        this.form.markAsDirty();
    }

    // ---------- Form construction ----------

    private buildForm(): FormGroup {
        const group: Record<string, FormGroup | FormArray> = {};

        for (const section of this.sections) {
            group[section.key] = section.repeated
                ? this.fb.array<FormGroup>([])
                : this.buildFieldGroup(section);
        }
        return this.fb.group(group);
    }

    private buildFieldGroup(section: SectionDescriptor): FormGroup {
        const controls: Record<string, unknown> = {};

        for (const field of section.fields) {
            const validators = [];
            if (field.required) {
                validators.push(Validators.required);
            }
            if (field.maxLength) {
                validators.push(Validators.maxLength(field.maxLength));
            }
            controls[field.key] = [null, validators];
        }
        return this.fb.group(controls);
    }

    private buildItemGroup(section: SectionDescriptor): FormGroup {
        const group = this.buildFieldGroup(section);

        // Structual keys ride along untouched so a round-trip never loses sync state.
        group.addControl('item_id', this.fb.control(this.newItemId()));
        group.addControl('display_order', this.fb.control(0));
        group.addControl('is_untranslated', this.fb.control(false));
        group.addControl('deleted_in_master', this.fb.control(false));

        // skill_id points at the catalogue; kept as-is so re-editing never breaks the reference.
        group.addControl('skill_id', this.fb.control(null));
        return group;
    }

    // ---------- Patching ----------

    private patch(content: CvContent): void {
        const raw = content as unknown as Record<string, unknown>;

        for (const section of this.sections) {
            if (!section.repeated) {
                (this.form.get(section.key) as FormGroup)
                    .patchValue(raw[section.key] ?? {}, { emitEvent: false });
                continue;
            }

            const array = this.arrayOf(section.key);
            array.clear({ emitEvent: false });

            const items = (raw[section.key] as Record<string, unknown>[] | undefined) ?? [];
            // Trust the stored order rather than the numbers, which a bad write could contradict.
            for (const item of items) {
                const group = this.buildItemGroup(section);
                group.patchValue(this.writeItem(section, item), { emitEvent: false });
                array.push(group, { emitEvent: false });
            }
        }
        this.form.markAsPristine();
    }

    // Wire shape into form values: a tag list becomes the comma-separated text the input shows.
    private writeItem(section: SectionDescriptor, item: Record<string, unknown>): Record<string, unknown> {
        const patched: Record<string, unknown> = { ...item };

        for (const field of section.fields) {
            if (field.kind === FieldKind.Tags) {
                const tags = (item[field.key] as string[] | undefined) ?? [];
                patched[field.key] = tags.join(', ');
            }
        }
        return patched;
    }
    
    // ---------- Reading ----------

    private readSingle(section: SectionDescriptor): Record<string, unknown> {
        const group = this.form.get(section.key) as FormGroup;
        const result: Record<string, unknown> = {};

        for (const field of section.fields) {
            result[field.key] = this.readField(field.kind, group.get(field.key)?.value);
        }
        return result;
    }

    private readItem(section: SectionDescriptor, group: FormGroup, index: number): Record<string, unknown> {
        const result: Record<string, unknown> = {
            item_id: group.get('item_id')?.value ?? this.newItemId(),
            // Position is the truth; the previously stored number is discarded.
            display_order: index,
            is_untranslated: group.get('is_untranslated')?.value === true,
            deleted_in_master: group.get('deleted_in_master')?.value === true,
        };

        if (section.key === 'skills') {
            result['skill_id'] = group.get('skill_id')?.value ?? null;
        }

        for (const field of section.fields) {
            result[field.key] = this.readField(field.kind, group.get(field.key)?.value);
        }

        return result;
    }

    private readField(kind: FieldKind, value: unknown): unknown {
        if (kind === FieldKind.Tags) {
            return String(value ?? '')
                .split(',')
                .map(tag => tag.trim())
                .filter(tag => tag.length > 0);
        }
        if (kind === FieldKind.Number) {
            const parsed = Number(value);
            return value === null || value === '' || Number.isNaN(parsed) ? null : parsed;
        }
        if (typeof value === 'string') {
            const trimmed = value.trim();
            return trimmed.length ? trimmed : null;
        }
        return value ?? null;
    }

    // ---------- Helpers ----------

    private arrayOf(key: string): FormArray<FormGroup> {
        return this.form.get(key) as FormArray<FormGroup>;
    }

    /*
     * crypto.randomUUID needs a secure context, which localhost and https both are - but a plain
     * http origin on the LAN is not and there it is simply undefined. Falling back keeps the
     * editor working there; the server fills in anything missing regardless.
     */
    private newItemId(): string {
        if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
            return crypto.randomUUID();
        }
        return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2, 10)}`;
    }
}