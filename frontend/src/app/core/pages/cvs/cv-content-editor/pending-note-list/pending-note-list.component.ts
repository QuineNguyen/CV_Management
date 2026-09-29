import { DatePipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, input } from "@angular/core";
import { PendingNoteResponse } from "../../../../dtos/update-request.dto";

/*
 * Read-only list of update request notes. One-way by design:
 * no reply box, unlike the review threads of the approval flow.
 */
@Component({
    selector: 'app-pending-note-list',
    standalone: true,
    imports: [DatePipe],
    templateUrl: './pending-note-list.component.html',
    styleUrl: './pending-note-list.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { '[class.is-plain]': 'plain()' },
})
export class PendingNoteListComponent {

    readonly notes = input.required<PendingNoteResponse[]>();
    readonly caption = input('Feedback from the pending update request');

    // Inside a popover the card already frames the list
    readonly plain = input(false);
}