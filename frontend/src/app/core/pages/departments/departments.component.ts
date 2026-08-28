import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { NgTemplateOutlet } from "@angular/common";
import { ChangeDetectionStrategy, Component, HostListener, inject, OnInit, signal } from "@angular/core";
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DepartmentService } from '../../services/department.service';
import { DialogMode } from '../../enums/dialog-mode.enum';
import { DepartmentNode, DepartmentRequest } from '../../dtos/department.dto';
import { DepartmentDialogState, DepartmentDropList, DepartmentPageState } from '../../models/department.model';
import { DepartmentFormDialogComponent } from './department-form/department-form-dialog.component';
import { ToastService } from '../../services/toast.service';

@Component({
    selector: 'app-departments',
    standalone: true,
    imports: [MatPaginatorModule, MatTooltipModule, NgTemplateOutlet, DragDropModule, DepartmentFormDialogComponent],
    templateUrl: './departments.component.html',
    styleUrl: './departments.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DepartmentsComponent implements OnInit {

    private readonly departmentService = inject(DepartmentService);
    private readonly toast = inject(ToastService);
    private static readonly DEFAULT_PAGE_SIZE = 20;

    readonly pageSizeOptions = [5, 10, 20, 50];

    readonly pageState = signal<DepartmentPageState>({
        index: 0,
        size: DepartmentsComponent.DEFAULT_PAGE_SIZE,
        total: 0,
    });

    readonly tree = signal<DepartmentNode[]>([]);
    readonly loading = signal(false);

    readonly expandedIds = signal<ReadonlySet<string>>(new Set<string>());

    readonly dialogState = signal<DepartmentDialogState | null>(null);
    readonly saving = signal(false);

    readonly deleteTarget = signal<DepartmentNode | null>(null);
    readonly deleting = signal(false);
    readonly isDeleteClosing = signal(false);

    ngOnInit(): void {
        this.loadTree(true);
    }

    // ---------- Loading ----------

    loadTree(showSpinner: boolean): void {
        if (showSpinner) {
            this.loading.set(true);
        }

        const { index, size } = this.pageState();

        this.departmentService.getTree({ page: index, size }).subscribe({
            next: result => {
                this.tree.set(result.content);
                this.pageState.update(state => ({ ...state, total: result.totalElements }));
                this.loading.set(false);
            },
            error: () => this.loading.set(false),
        });
    }

    onPageChange(event: PageEvent): void {
        this.pageState.update(state => ({ ...state, index: event.pageIndex, size: event.pageSize }));
        this.loadTree(true);
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
        this.dialogState.set({
            mode: DialogMode.Create,
            department: null,
            parentId
        });
    }

    openEdit(department: DepartmentNode): void {
        this.dialogState.set({
            mode: DialogMode.Edit,
            department,
            parentId: department.parentDepartmentId,
        });
    }

    closeDialog(): void {
        this.dialogState.set(null);
        this.saving.set(false);
    }

    submitDialog(body: DepartmentRequest): void {
        const state = this.dialogState();
        if (!state) {
            return;
        }
        this.saving.set(true);
        const isEdit = state.mode === DialogMode.Edit && state.department;

        // The '$' suffix (Finnish Notation) signifies that this variable holds an RxJS Observable stream
        // rather than a plain synchronous value, indicating it needs to be subscribed to.
        const request$ = isEdit
            ? this.departmentService.update(state.department!.id, body)
            : this.departmentService.create(body);

        request$.subscribe({
            next: saved => {
                this.saving.set(false);
                this.closeDialog();
                this.toast.success(
                    isEdit
                        ? `Department ${saved.code} updated`
                        : `Department ${saved.code} created`
                );

                this.loadTree(false);
            },
            error: () => this.saving.set(false),
        });
    }

    // ---------- Delete ----------

    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (this.deleteTarget()) {
            this.cancelDelete();
        }
    }

    askDelete(department: DepartmentNode): void {
        this.isDeleteClosing.set(false);
        this.deleteTarget.set(department);
    }

    cancelDelete(): void {
        if (this.isDeleteClosing() || this.deleting()) {
            return;
        }
        this.isDeleteClosing.set(true);
        setTimeout(() => {
            this.deleteTarget.set(null);
            this.deleting.set(false);
            this.isDeleteClosing.set(false);
        }, 500);
    }

    confirmDelete(): void {
        const target = this.deleteTarget();
        if (!target) {
            return;
        }
        this.deleting.set(true);
        this.departmentService.delete(target.id).subscribe({
            next: () => {
                this.deleteTarget.set(null);
                this.deleting.set(false);
                this.isDeleteClosing.set(false);
                this.toast.success(`Deleted department ${target.code}`);
                this.loadTree(false);
            },
            error: () => {
                this.deleting.set(false);
                this.deleteTarget.set(null);
                this.isDeleteClosing.set(false);
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

        const index = event.currentIndex;
        const moved = nodes[index];

        this.departmentService.move(moved.id, {
            parentDepartmentId: parentId,
            afterDepartmentId: index > 0 ? nodes[index - 1].id : null,
            beforeDepartmentId: index < nodes.length - 1 ? nodes[index + 1].id : null,
        }).subscribe({
            error: () => this.loadTree(false),
        })
    }

    // ---------- Helpers ----------
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
}