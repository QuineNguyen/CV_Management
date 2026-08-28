import { CvProfileResponse } from "../dtos/cv-profile.dto";
import { DialogMode } from "../enums/dialog-mode.enum";

export interface CvProfileDialogState {
    mode: DialogMode;
    profile: CvProfileResponse | null;
    employeeId: string;
}

export interface CvProfilePageState {
    index: number;
    size: number;
    total: number;
}