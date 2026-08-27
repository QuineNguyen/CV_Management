import { TeamResponse } from "../dtos/team.dto";
import { DialogMode } from "../enums/dialog-mode.enum";

// Paginator state for the team list.
export interface TeamPageState {
    index: number;
    size: number;
    total: number;
}

// Filters applied to the team list.
export interface TeamFilterState {
    keyword: string;
}

// Drives the create/edit dialog. `team` is null when creating.
export interface TeamDialogState {
    mode: DialogMode;
    team: TeamResponse | null;
}