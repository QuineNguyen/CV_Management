import { ChangeDetectionStrategy, Component, ElementRef, HostListener, inject, input, signal } from "@angular/core";
import { PendingNoteListComponent } from "../pending-note-list/pending-note-list.component";
import { PendingNoteResponse } from "../../../../dtos/update-request.dto";

let nextPanelId = 0;

/*
 * Marker next to a field label for the notes pinned to that field.
 * - Hover shows the notes with a mouse; a tap toggles them, since touch has no hover.
 * - The open state is also a host class, so the parent .field can rise above its neighbours.
 */
@Component({
    selector: 'app-pending-note-badge',
    standalone: true,
    imports: [PendingNoteListComponent],
    templateUrl: './pending-note-badge.component.html',
    styleUrl: './pending-note-badge.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { '[class.is-open]': 'open()' },
})
export class PendingNoteBadgeComponent {

    private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

    readonly notes = input.required<PendingNoteResponse[]>();
    readonly open = signal(false);
    readonly panelId = `pending-note-panel-${nextPanelId++}`;

    toggle(): void {
        this.open.update(open => !open);
    }

    // Outside click closes; clicks inside the chip or the panel keep it open
    @HostListener('document:click', ['$event'])
    onDocumentClick(event: MouseEvent): void {
        if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
            this.open.set(false);
        }
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        this.open.set(false);
    }
}