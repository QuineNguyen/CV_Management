import { ChangeDetectionStrategy, Component, computed, HostListener, input, output, signal } from "@angular/core";
import { CvContent } from "../../../models/cv-content.model";
import { AnchorItemOption, anchorLabelOf, INLINE_COMMENT_MAX_LENGTH, itemTitleOf, PendingInlineComment, sectionOf } from "../../../models/inline-comment.model";
import { CV_SECTIONS } from "../../../models/cv-section-descriptor.model";
import { CvSectionKey } from "../../../enums/cv-section-key.enum";

/*
 * Picks (section, entry, field) and the text of one inline comment.
 * - Entry is shown only for REPEATED sections and is then required.
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
    readonly added = output<PendingInlineComment>();

    readonly sections = CV_SECTIONS;
    readonly maxLength = INLINE_COMMENT_MAX_LENGTH;

    readonly sectionKey = signal("");
    readonly itemId = signal("");
    readonly fieldKey = signal("");
    readonly text = signal("");

    readonly section = computed(() => sectionOf(this.sectionKey()) ?? null);

    // Entries of the chosen section, as the draft currently holds them.
    readonly items = computed<AnchorItemOption[]>(() => {
        const section = this.section();
        if (!section?.repeated) {
            return [];
        }
        const raw = (this.content() as unknown as Record<string, Record<string, unknown>[] | undefined>)[section.key] ?? [];
        return raw
            .filter(item => !!item["item_id"])
            .map((item, index) => ({ id: String(item["item_id"]), label: itemTitleOf(section, item, index) }));
    });

    readonly canAdd = computed(() => {
        const section = this.section();
        if (!section || !this.text().trim()) {
            return false;
        }
        return !section.repeated || !!this.itemId();
    });

    readonly openSelectKey = signal<'section' | 'item' | 'field' | null>(null);

    readonly selectedSectionLabel = computed(() =>
        this.sections.find(option => option.key === this.sectionKey())?.label ?? 'Select a section'
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

    toggleSelect(key: 'section' | 'item' | 'field', event: MouseEvent): void {
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