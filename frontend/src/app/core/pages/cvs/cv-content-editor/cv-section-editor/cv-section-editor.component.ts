import { CdkDragDrop, CdkDropList } from "@angular/cdk/drag-drop";
import { ChangeDetectionStrategy, Component, computed, HostListener, inject, input, output, signal } from "@angular/core";
import { FormArray, FormGroup, ReactiveFormsModule } from "@angular/forms";
import { MatDatepickerModule } from "@angular/material/datepicker";
import { DateAdapter, MAT_DATE_FORMATS, MatNativeDateModule } from "@angular/material/core";
import { CvItemEditorComponent } from "../cv-item-editor/cv-item-editor.component";
import { FieldDescriptor, FieldKind, notSpecifiedLabel, optionsFor, SectionDescriptor, SelectOption } from "../../../../models/cv-section-descriptor.model";
import { CvLanguage } from "../../../../enums/cv-language.enum";
import { CustomDateAdapter, DD_MM_YYYY_FORMATS } from "../../../../utils/app-date-adapter.util";
import { InlineCommentStore } from "../../../../services/inline-comment-store.service";
import { InlineCommentThreadComponent } from "../../../approvals/inline-comment-thread/inline-comment-thread.component";
import { InlineCommentThread } from "../../../../models/inline-comment.model";

/*
 * One of the nine sections. SINGLE sections render their fields directly; REPEATED ones render a
 * reorderable list of CvItemEditorComponent.
 * 
 * - Reordering emits an event rather than writing display_order here: the order of the FormArray
 * is the source of truth and the parent renumbers on save, so a drag that is later abandoned costs
 * nothing.
 * - Comments of a SINGLE section sit under its header; comments of an entry sit inside that entry.
 */
@Component({
    selector: 'app-cv-section-editor',
    standalone: true,
    imports: [ReactiveFormsModule, CdkDropList, CvItemEditorComponent, MatDatepickerModule, MatNativeDateModule, InlineCommentThreadComponent],
    providers: [
        { provide: DateAdapter, useClass: CustomDateAdapter },
        { provide: MAT_DATE_FORMATS, useValue: DD_MM_YYYY_FORMATS },
    ],
    templateUrl: './cv-section-editor.component.html',
    styleUrl: './cv-section-editor.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvSectionEditorComponent {

    private readonly commentStore = inject(InlineCommentStore);

    readonly FieldKind = FieldKind;

    readonly section = input.required<SectionDescriptor>();
    readonly control = input.required<FormGroup | FormArray>();
    readonly readOnly = input(false);
    readonly language = input.required<CvLanguage>();

    readonly itemAdded = output<void>();
    readonly itemRemoved = output<number>();
    readonly itemsReordered = output<CdkDragDrop<unknown>>();

    // Only one entry is open at a time; a long section stays scannable.
    readonly expandedIndex = signal<number | null>(null);

    readonly openSelectKey = signal<string | null>(null);

    // SINGLE sections have no entries, so their comments anchor on the section itself.
    readonly sectionThreads = computed(() =>
        this.section().repeated ? [] : this.commentStore.threadsFor(this.section().key, null));

    /*
     * Threads whose entry the owner has since removed. Shown here so a comment never disappears
     * just because its anchor did.
     */
    orphanThreads(): InlineCommentThread[] {
        if (!this.section().repeated) {
            return [];
        }
        const liveIds = new Set(this.itemGroups.map(group => group.get('item_id')?.value as string));
        return this.commentStore.threads().filter(thread =>
            thread.root.sectionKey === this.section().key
            && !!thread.root.itemId
            && !liveIds.has(thread.root.itemId)
        );
    }

    get singleGroup(): FormGroup {
        return this.control() as FormGroup;
    }

    get itemsArray(): FormArray<FormGroup> {
        return this.control() as FormArray<FormGroup>;
    }

    get itemGroups(): FormGroup[] {
        return this.itemsArray.controls;
    }

    hasError(field: FieldDescriptor, error: string): boolean {
        const control = this.singleGroup.get(field.key);
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
        const control = this.singleGroup.get(fieldKey);
        if (control) {
            control.setValue(value);
            control.markAsDirty();
            control.markAsTouched();
        }
        this.openSelectKey.set(null);
    }

    options(field: FieldDescriptor): readonly SelectOption[] {
        return field.optionSet ? optionsFor(field.optionSet, this.language()) : [];
    }

    emptyOptionLabel(): string {
        return notSpecifiedLabel(this.language());
    }

    getSelectedOptionLabel(field: FieldDescriptor): string {
        const value = this.singleGroup.get(field.key)?.value;
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

    onToggle(index: number): void {
        this.expandedIndex.update(current => (current === index ? null : index));
    }

    onAdd(): void {
        this.itemAdded.emit();
        // A newly added entry opens straight away: an empty collapsed row looks like nothing happened.
        this.expandedIndex.set(this.itemsArray.length - 1);
    }

    onRemove(index: number): void {
        this.itemRemoved.emit(index);
        this.expandedIndex.set(null);
    }

    onDrop(event: CdkDragDrop<unknown>): void {
        this.itemsReordered.emit(event);
        this.expandedIndex.set(null);
    }
}