import { ChangeDetectionStrategy, Component, computed, HostListener, input, output, signal } from "@angular/core";
import { CvContent } from "../../../models/cv-content.model";
import { AnchorItemOption, anchorLabelOf, INLINE_COMMENT_MAX_LENGTH, itemTitleOf, PendingInlineComment, sectionOf } from "../../../models/inline-comment.model";
import { CV_SECTIONS } from "../../../models/cv-section-descriptor.model";
import { CvSectionKey } from "../../../enums/cv-section-key.enum";
import { AnchorSelectKey } from "../../../enums/inline-comment-status.enum";
import { anchorableEntriesOf, isSectionFilled, itemIdOf } from "../../../utils/cv-anchor.util";

/*
 * Picks (section, entry, field) and the text of one inline comment.
 * - Entry is shown only for REPEATED sections and is then required.
 * - Empty sections (no entries or no filled field) are hidden and the reviewer is pointed
 * to the parent's overall field instead; a CV with nothing filled in shows only that notice.
 * - Field is optional and limited to the fields of the chosen section, so the anchor always
 * names something the editor can render.
 * - Uses custom-select-wrap dropdown matching CV item editor (e.g. Proficiency select).
 */
@Component({
    selector: 'app-anchor-picker',
    standalone: true,
    templateUrl: './anchor-picker.component.html',
    styleUrl: './anchor-picker.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnchorPickerComponent {

    readonly content = input.required<CvContent>();
    readonly textLabel = input<string>('Comment');
    readonly placeholder = input<string>('What is wrong here and what should it say');
    readonly submitLabel = input<string>('Add comment');
    readonly maxLength = input<number>(INLINE_COMMENT_MAX_LENGTH);
    // Label of the parent's free-text field that covers what cannot be anchored
    readonly fallbackFieldLabel = input<string>('Overall reason');
    readonly added = output<PendingInlineComment>();
    
    readonly selectKey = AnchorSelectKey;

    readonly sectionKey = signal("");
    readonly itemId = signal("");
    readonly fieldKey = signal("");
    readonly text = signal("");

    // Only sections with something to point at: an entry or a filled field
    readonly sections = computed(() =>
        CV_SECTIONS.filter(section => isSectionFilled(this.content(), section)));

    readonly emptySectionLabels = computed(() => CV_SECTIONS
        .filter(section => !isSectionFilled(this.content(), section))
        .map(section => section.label));

    // Tells the reviewer why a section is missing and where to write instead
    readonly emptySectionsHint = computed(() => {
        const labels = this.emptySectionLabels();
        if (!labels.length) {
            return null;
        }
        const target = `"${this.fallbackFieldLabel()}"`;
        if (!this.sections().length) {
            return `This CV is still empty. Write your feedback in ${target}.`;
        }
        const [verb, pronoun] = labels.length === 1 ? ['is', 'it'] : ['are', 'them'];
        return `${labels.join(', ')} ${verb} still empty. Mention ${pronoun} in ${target}`;
    });

    // Looked up in the visible list, so a section that became empty drops out
    readonly section = computed(() => 
        this.sections().find(section => section.key === this.sectionKey()) ?? null);

    // Entries of the chosen section, as the draft currently holds them.
    readonly items = computed<AnchorItemOption[]>(() => {
        const section = this.section();
        if (!section?.repeated) {
            return [];
        }
        return anchorableEntriesOf(this.content(), section).map((item, index) => ({
            id: itemIdOf(item),
            label: itemTitleOf(section, item, index),
        }));
    });

    readonly canAdd = computed(() => {
        const section = this.section();
        if (!section || !this.text().trim()) {
            return false;
        }
        return !section.repeated || !!this.itemId();
    });

    readonly openSelectKey = signal<AnchorSelectKey | null>(null);

    readonly selectedSectionLabel = computed(() =>
        this.section()?.label ?? 'Select a section'
    );

    readonly selectedItemLabel = computed(() =>
        this.items().find(item => item.id === this.itemId())?.label ?? 'Select an entry'
    );

    readonly selectedFieldLabel = computed(() => {
        const section = this.section();
        if (!section) {
            return '';
        }
        const field = section.fields.find(f => f.key === this.fieldKey());
        if (field) {
            return field.label;
        }
        return section.repeated ? 'Whole entry' : 'Whole section';
    });

    toggleSelect(key: AnchorSelectKey, event: MouseEvent): void {
        event.stopPropagation();
        this.openSelectKey.update(current => (current === key ? null : key));
    }

    selectSection(key: string): void {
        this.sectionKey.set(key);
        this.itemId.set("");
        this.fieldKey.set("");
        this.openSelectKey.set(null);
    }

    selectItem(id: string): void {
        this.itemId.set(id);
        this.openSelectKey.set(null);
    }

    selectField(key: string): void {
        this.fieldKey.set(key);
        this.openSelectKey.set(null);
    }

    @HostListener('document:click')
    closeSelects(): void {
        this.openSelectKey.set(null);
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        this.openSelectKey.set(null);
    }

    onTextInput(event: Event): void {
        this.text.set((event.target as HTMLTextAreaElement).value);
    }

    add(): void {
        const section = this.section();
        if (!section || !this.canAdd()) {
            return;
        }

        const itemId = section.repeated ? this.itemId() : null;
        const fieldKey = this.fieldKey() || null;
        const itemTitle = this.items().find(item => item.id === itemId)?.label ?? null;

        this.added.emit({
            localId: `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`,
            request: {
                sectionKey: section.key as CvSectionKey,
                itemId,
                fieldKey,
                content: this.text().trim(),
            },
            anchorLabel: anchorLabelOf(section.key, itemTitle, fieldKey),
        });

        // Keep the section: consecutive comments usually land in the same area.
        this.openSelectKey.set(null);
        this.itemId.set("");
        this.fieldKey.set("");
        this.text.set("");
    }
}