import { ChangeDetectionStrategy, Component, computed, ElementRef, HostListener, inject, input, output, signal, viewChild } from "@angular/core";
import { FormControl, ReactiveFormsModule } from "@angular/forms";
import { SkillService } from "../../../../services/skill.service";
import { SkillOptionResponse } from "../../../../dtos/skill.dto";
import { catchError, debounceTime, map, merge, of, Subject, switchMap } from "rxjs";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";

/*
 * Multi-select picker over the skill catalogue.
 * - Options come from the server in catalogue order (display_order, name) and are never
 * narrowed here; typing asks the server again.
 * - Selected skills stay in the list with a check mark, so un-picking works from either place.
 */
@Component({
    selector: 'app-skill-filter',
    standalone: true,
    imports: [ReactiveFormsModule],
    templateUrl: './skill-filter.component.html',
    styleUrl: './skill-filter.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SkillFilterComponent {

    private static readonly SEARCH_DEBOUNCE_MS = 250;
    private static readonly OPTION_PAGE_SIZE = 20;
    // Same cap as the server's skillIds limit
    private static readonly MAX_SELECTED = 10;

    private readonly skillService = inject(SkillService);
    private readonly boxRef = viewChild<ElementRef<HTMLElement>>('box');
    private readonly panelRef = viewChild<ElementRef<HTMLElement>>('panel');
    private readonly inputRef = viewChild<ElementRef<HTMLInputElement>>('input');

    readonly selected = input<SkillOptionResponse[]>([]);
    readonly selectedChange = output<SkillOptionResponse[]>();

    readonly maxSelected = SkillFilterComponent.MAX_SELECTED;
    readonly query = new FormControl('', { nonNullable: true });
    readonly open = signal(false);
    readonly loading = signal(false);
    readonly options = signal<SkillOptionResponse[]>([]);
    readonly total = signal(0);

    readonly selectedIds = computed(() => new Set(this.selected().map(skill => skill.id)));
    readonly atLimit = computed(() => this.selected().length >= SkillFilterComponent.MAX_SELECTED);
    readonly placeholder = computed(() => this.selected().length ? 'Add skill' : 'Pick from the catalogue');

    // Fires immediately when the panel opens, debounced while typing
    private readonly immediateTerms = new Subject<string>();

    constructor() {
        merge(
            this.immediateTerms,
            this.query.valueChanges.pipe(debounceTime(SkillFilterComponent.SEARCH_DEBOUNCE_MS)),
        ).pipe(
            map(term => term.trim()),
            switchMap(term => {
                this.loading.set(true);
                return this.skillService.listOptions({
                    keyword: term || undefined,
                    page: 0,
                    size: SkillFilterComponent.OPTION_PAGE_SIZE,
                }).pipe(catchError(() => of(null)));
            }),
            takeUntilDestroyed(),
        ).subscribe(page => {
            this.loading.set(false);
            if (page) {
                this.options.set(page.content);
                this.total.set(page.totalElements);
            }
        });
    }

    openPanel(): void {
        if (this.open()) {
            return;
        }
        this.open.set(true);
        this.immediateTerms.next(this.query.value);
    }

    isSelected(skill: SkillOptionResponse): boolean {
        return this.selectedIds().has(skill.id);
    }

    toggle(skill: SkillOptionResponse): void {
        if (this.isSelected(skill)) {
            this.remove(skill);
            return;
        }
        if (this.atLimit()) {
            return;
        }
        this.selectedChange.emit([...this.selected(), skill]);
        this.clearQuery();
    }

    remove(skill: SkillOptionResponse): void {
        this.selectedChange.emit(this.selected().filter(item => item.id !== skill.id));
    }

    private clearQuery(): void {
        if (this.query.value) {
            this.query.setValue('', { emitEvent: false });
            this.immediateTerms.next('');
        }
        this.inputRef()?.nativeElement.focus();
    }

    // Closes on any click outside the skill box and picker panel
    @HostListener('document:click', ['$event'])
    onDocumentClick(event: MouseEvent): void {
        if (!this.open()) {
            return;
        }
        const target = event.target as Node | null;
        const inBox = !!(target && this.boxRef()?.nativeElement.contains(target));
        const inPanel = !!(target && this.panelRef()?.nativeElement.contains(target));
        if (!inBox && !inPanel) {
            this.open.set(false);
        }
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        this.open.set(false);
    }
}