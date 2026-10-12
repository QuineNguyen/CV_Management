import { SkillResponse } from "../dtos/skill.dto";
import { DialogMode } from "../enums/dialog-mode.enum";

export interface SkillPageState {
    index: number;
    size: number;
    total: number;
}

// skill is null when adding
export interface SkillDialogState {
    mode: DialogMode;
    skill: SkillResponse | null;
}