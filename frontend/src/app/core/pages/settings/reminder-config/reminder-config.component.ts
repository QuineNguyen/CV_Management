import { DatePipe } from "@angular/common";
import { MatSlideToggleModule } from "@angular/material/slide-toggle";
import { ChangeDetectionStrategy, Component, computed, DestroyRef, HostListener, inject, OnInit, signal } from "@angular/core";
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { SystemConfigService } from "../../../services/system-config.service";
import { ToastService } from "../../../services/toast.service";
import { SYSTEM_CONFIG_LIMITS } from "../../../models/system-config.model";
import { SystemConfigResponse, UpdateSystemConfigRequest } from "../../../dtos/system-config.dto";
import { takeUntilDestroyed, toSignal } from "@angular/core/rxjs-interop";
import { map } from "rxjs";

type DayField = 'escalationThresholdDays' | 'approvalSlaDays';

/*
 * Reminder schedule and approval SLA (Admin).
 * - Values come back parsed by the server, so the form shows what the scheduler actually runs.
 * - A new SLA reaches only assignments created after saving; open ones keep their deadline.
 */
@Component({
    selector: 'app-reminder-config',
    standalone: true,
    imports: [ReactiveFormsModule, MatSlideToggleModule, DatePipe],
    templateUrl: './reminder-config.component.html',
    styleUrl: './reminder-config.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReminderConfigComponent implements OnInit {

    private static readonly WHOLE_NUMBER = /^\d+$/;
    private static readonly HOUR_DIGITS = 2;

    private readonly configService = inject(SystemConfigService);
    private readonly toast = inject(ToastService);
    private readonly fb = inject(NonNullableFormBuilder);
    private readonly destroyRef = inject(DestroyRef);

    readonly limits = SYSTEM_CONFIG_LIMITS;

    // "00:00" .. "23:00"
    readonly hourOptions = Array.from(
        { length: SYSTEM_CONFIG_LIMITS.maxHour - SYSTEM_CONFIG_LIMITS.minHour + 1 },
        (_, offset) => {
            const value = SYSTEM_CONFIG_LIMITS.minHour + offset;
            return { value, label: `${String(value).padStart(ReminderConfigComponent.HOUR_DIGITS, '0')}:00` };
        },
    );

    readonly hourDropdownOpen = signal(false);
    readonly loading = signal(true);
    readonly loadFailed = signal(false);
    readonly saving = signal(false);
    readonly saved = signal<SystemConfigResponse | null>(null);

    // Placeholders only: the form stays hidden until the server values arrive
    readonly form = this.fb.group({
        reminderEnabled: [true],
        reminderSendHour: [SYSTEM_CONFIG_LIMITS.minHour as number, [Validators.required]],
        escalationThresholdDays: [SYSTEM_CONFIG_LIMITS.minDays as number, this.dayValidators()],
        approvalSlaDays: [SYSTEM_CONFIG_LIMITS.minDays as number, this.dayValidators()],
    });

    private readonly formValue = toSignal(
        this.form.valueChanges.pipe(map(() => this.form.getRawValue())),
        { initialValue: this.form.getRawValue() }
    );

    readonly selectedHourLabel = computed(() => {
        const current = this.formValue().reminderSendHour;
        const option = this.hourOptions.find(o => o.value === Number(current));
        return option ? option.label : '09:00';
    });

    readonly remindersOn = computed(() => this.formValue().reminderEnabled ?? true);

    // Compared with the last saved values, so typing a value back clears the change
    readonly hasChanges = computed(() => {
        const saved = this.saved();
        if (!saved) {
            return false;
        }
        const current = this.formValue();
        return current.reminderEnabled !== saved.reminderEnabled
            || Number(current.reminderSendHour) !== saved.reminderSendHour
            || Number(current.escalationThresholdDays) !== saved.escalationThresholdDays
            || Number(current.approvalSlaDays) !== saved.approvalSlaDays;
    });

    readonly slaChanged = computed(() => {
        const saved = this.saved();
        return !!saved && Number(this.formValue().approvalSlaDays) !== saved.approvalSlaDays;
    });

    ngOnInit(): void {
        this.form.controls.reminderEnabled.valueChanges
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(enabled => this.updateEscalationThresholdState(enabled));

        this.load();
    }

    load(): void {
        this.loading.set(true);
        this.loadFailed.set(false);

        this.configService.get()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: config => {
                    this.apply(config);
                    this.loading.set(false);
                },
                error: () => {
                    this.loadFailed.set(true);
                    this.loading.set(false);
                },
            });
    }

    hasError(field: DayField, error: string): boolean {
        const control = this.form.controls[field];
        return control.touched && control.hasError(error);
    }

    toggleHourDropdown(event: MouseEvent): void {
        if (!this.remindersOn()) {
            return;
        }
        event.stopPropagation();
        this.hourDropdownOpen.update(open => !open);
    }

    selectHour(value: number): void {
        this.form.controls.reminderSendHour.setValue(value);
        this.form.controls.reminderSendHour.markAsDirty();
        this.hourDropdownOpen.set(false);
    }

    @HostListener('document:click')
    closeDropdowns(): void {
        if (this.hourDropdownOpen()) {
            this.hourDropdownOpen.set(false);
        }
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        this.closeDropdowns();
    }

    discard(): void {
        this.hourDropdownOpen.set(false);
        const saved = this.saved();
        if (saved) {
            this.apply(saved);
        }
    }

    // Validation errors on fields come back as 400 and are rendered inline; others are toasted centrally
    save(): void {
        if (this.form.invalid) {
            this.form.markAllAsTouched();
            return;
        }
        if (!this.hasChanges()) {
            return;
        }

        const value = this.form.getRawValue();
        const body: UpdateSystemConfigRequest = {
            reminderEnabled: value.reminderEnabled,
            reminderSendHour: Number(value.reminderSendHour),
            escalationThresholdDays: Number(value.escalationThresholdDays),
            approvalSlaDays: Number(value.approvalSlaDays),
        };

        this.saving.set(true);
        this.configService.update(body).subscribe({
            next: config => {
                this.apply(config);
                this.saving.set(false);
                this.toast.success('Configuration saved');
            },
            error: () => this.saving.set(false),
        });
    }

    // ---------- Internals ----------

    private apply(config: SystemConfigResponse): void {
        this.saved.set(config);
        this.form.setValue({
            reminderEnabled: config.reminderEnabled,
            reminderSendHour: config.reminderSendHour,
            escalationThresholdDays: config.escalationThresholdDays,
            approvalSlaDays: config.approvalSlaDays,
        });
        this.updateEscalationThresholdState(config.reminderEnabled);
        this.form.markAsPristine();
        this.form.markAsUntouched();
    }

    private updateEscalationThresholdState(enabled: boolean): void {
        const control = this.form.controls.escalationThresholdDays;
        if (enabled) {
            if (control.disabled) {
                control.enable({ emitEvent: false });
            }
        } else {
            if (control.enabled) {
                control.disable({ emitEvent: false });
            }
        }
    }

    private dayValidators() {
        return [
            Validators.required,
            Validators.min(SYSTEM_CONFIG_LIMITS.minDays),
            Validators.max(SYSTEM_CONFIG_LIMITS.maxDays),
            Validators.pattern(ReminderConfigComponent.WHOLE_NUMBER),
        ];
    }
}