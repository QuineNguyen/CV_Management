import { BatchWizardStep } from "../enums/batch-request.enum";

export interface BatchStepItem {
    step: BatchWizardStep;
    label: string;
}

export const BATCH_WIZARD_STEPS: readonly BatchStepItem[] = [
    { step: BatchWizardStep.Criteria, label: 'Choose recipients' },
    { step: BatchWizardStep.Preview, label: 'Preview' },
    { step: BatchWizardStep.Confirm, label: 'Confirm' },
];

export interface BatchPageState {
    index: number;
    size: number;
    total: number;
}