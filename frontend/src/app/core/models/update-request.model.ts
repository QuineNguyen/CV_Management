export interface UpdateRequestPageState {
    index: number;
    size: number;
    total: number;
}

// One choice in the note anchor pickers: a section, an entry or a field
export interface AnchorOption {
    value: string;
    label: string;
}