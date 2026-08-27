import { UserResponse } from "../dtos/user.dto";
import { DialogMode } from "../enums/dialog-mode.enum";

export interface UserPageState {
    index: number;
    size: number;
    total: number;
}

export interface UserDialogState {
    mode: DialogMode;
    user: UserResponse | null;
}

export interface TemporaryPasswordState {
    title: string;
    fullName: string;
    password: string;
    copied: boolean;
}

export interface AvatarView {
    initials: string;
    // Hue index 0-5, mapped to a palette in CSS so colours stay consistent per person
    tone: number;
}