import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { NgTemplateOutlet } from "@angular/common";
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from "@angular/core";
import { DepartmentService } from '../../services/department.service';
import { DepartmentDialogMode } from '../../enums/department-dialog-mode.enum';
import { DepartmentNode, DepartmentRequest } from '../../dtos/department.dto';
import { DepartmentDialogState, DepartmentDropList, FlatDepartment } from '../../models/department.model';
import { HttpErrorResponse } from '@angular/common/http';
import { ERROR_MESSAGES } from '../../error-messages';
import { ClientErrorCode } from '../../models/error-code.model';
import { DepartmentFormDialogComponent } from './department-form/department-form-dialog.component';

@Component({
    selector: 'app-departments',
    standalone: true,
    imports: [NgTemplateOutlet, DragDropModule, DepartmentFormDialogComponent],
    templateUrl: './departments.component.html',
    styleUrl: './departments.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DepartmentsComponent implements OnInit {

    private readonly departmentService = inject(DepartmentService);

    readonly DialogMode = DepartmentDialogMode;

    readonly tree = signal<DepartmentNode[]>([]);
    readonly loading = signal(false);
    readonly pageError = signal<string | null>(null);

    readonly expandedIds = signal<ReadonlySet<string>>(new Set<string>());

    readonly dialogState = signal<DepartmentDialogState | null>(null);
    readonly dialogError = signal<string | null>(null);
    readonly saving = signal(false);

    readonly deleteTarget = signal<DepartmentNode | null>(null);
    readonly deleting = signal(false);

    // Parent dropdown source, indented by depth.
    readonly flatDepartments = computed(() => this.flatten(this.tree(), 0));

    readonly totalCount = computed(() => this.flatDepartments().length);

    ngOnInit(): void {
        this.loadTree(true);
    }

    // ---------- Loading ----------

    loadTree(showSpinner: boolean): void {
        if (showSpinner) {
            this.loading.set(true);
        }
        this.pageError.set(null);

        this.departmentService.getTree().subscribe({
            next: nodes => {
                this.tree.set(nodes);
                this.expandAllByDefault(nodes);
                this.loading.set(false);
            },
            error: (error: HttpErrorResponse) => {
                this.pageError.set(this.toMessage(error));
                this.loading.set(false);
            }
        })
    }

    // ---------- Expand / collapse ----------

    isExpanded(id: string): boolean {
        return this.expandedIds().has(id);
    }

    toggle(id: string): void {
        const next = new Set(this.expandedIds());
        next.has(id) ? next.delete(id) : next.add(id);
        this.expandedIds.set(next);
    }

    expandAll(): void {
        this.expandAllByDefault(this.tree());
    }

    collapseAll(): void {
        this.expandedIds.set(new Set<string>());
    }

    // ---------- Dialog ----------

    openCreate(parentId: string | null): void {
        this.dialogError.set(null);
        this.dialogState.set({
            mode: DepartmentDialogMode.Create,
            department: null,
            parentId
        });
    }

    openEdit(department: DepartmentNode): void {
        this.dialogError.set(null);
        this.dialogState.set({
            mode: DepartmentDialogMode.Edit,
            department,
            parentId: department.parentDepartmentId,
        });
    }

    closeDialog(): void {
        this.dialogState.set(null);
        this.dialogError.set(null);
        this.saving.set(false);
    }

    submitDialog(body: DepartmentRequest): void {
        const state = this.dialogState();
        if (!state) {
            return;
        }
        this.saving.set(true);
        this.dialogError.set(null);

        // The '$' suffix (Finnish Notation) signifies that this variable holds an RxJS Observable stream
        // rather than a plain synchronous value, indicating it needs to be subscribed to.
        const request$ = state.mode === DepartmentDialogMode.Edit && state.department
            ? this.departmentService.update(state.department.id, body)
            : this.departmentService.create(body);

        request$.subscribe({
            next: () => {
                this.saving.set(false);
                this.closeDialog();
                this.loadTree(false);
            },
            error: (error: HttpErrorResponse) => {
                this.saving.set(false);
                this.dialogError.set(this.toMessage(error));
            },
        });
    }

    // ---------- Delete ----------

    askDelete(department: DepartmentNode): void {
        this.pageError.set(null);
        this.deleteTarget.set(department);
    }

    cancelDelete(): void {
        this.deleteTarget.set(null);
        this.deleting.set(false);
    }

    confirmDelete(): void {
        const target = this.deleteTarget();
        if (!target) {
            return;
        }
        this.deleting.set(true);
        this.departmentService.delete(target.id).subscribe({
            next: () => {
                this.cancelDelete();
                this.loadTree(false);
            },
            error: (error: HttpErrorResponse) => {
                this.saving.set(false);
                this.dialogError.set(this.toMessage(error));
            },
        });
    }

    // ---------- Drag and drop ----------

    // Reordering is allowed inside one sibling list only, so no connected lists
    onDrop(event: CdkDragDrop<DepartmentDropList>): void {
        if (event.previousIndex === event.currentIndex) {
            return;
        }

        const { parentId, nodes } = event.container.data;
        moveItemInArray(nodes, event.previousIndex, event.currentIndex);
        this.tree.update(current => [...current]); // Repaint the mutated branch

        const orderedIds = nodes.map(node => node.id);
        this.departmentService.reorder(parentId, { orderedIds }).subscribe({
            error: (error: HttpErrorResponse) => {
                this.pageError.set(this.toMessage(error));
                this.loadTree(false); // Roll back to the server state
            }
        })
    }

    // ---------- Helpers ----------

    trackById(_: number, node: DepartmentNode): string {
        return node.id;
    }

    private expandAllByDefault(nodes: DepartmentNode[]): void {
        const ids = new Set<string>();
        const walk = (list: DepartmentNode[]): void => {
            for (const node of list) {
                if (node.children.length) {
                    ids.add(node.id);
                    walk(node.children);
                }
            }
        }
        walk(nodes);
        this.expandedIds.set(ids);
    }

    private flatten(nodes: DepartmentNode[], depth: number): FlatDepartment[] {
        return nodes.flatMap(node => [
            { id: node.id, code: node.code, name: node.name, depth },
            ...this.flatten(node.children, depth + 1),
        ]);
    }

    private toMessage(error: HttpErrorResponse): string {
        const code = error?.error?.code as ClientErrorCode | undefined;
        return (code && code in ERROR_MESSAGES && ERROR_MESSAGES[code]) || 'An error occured, please try again.';
    }
}