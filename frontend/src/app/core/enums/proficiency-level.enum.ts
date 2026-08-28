export enum ProficiencyLevel {
    Basic = 'BASIC',
    Elementary = 'ELEMENTARY',
    Intermediate = 'INTERMEDIATE',
    Advanced = 'ADVANCED',
    Expert = 'EXPERT',
}

export const PROFICIENCY_LEVEL_VALUES: Record<ProficiencyLevel, number> = {
    [ProficiencyLevel.Basic]: 1,
    [ProficiencyLevel.Elementary]: 2,
    [ProficiencyLevel.Intermediate]: 3,
    [ProficiencyLevel.Advanced]: 4,
    [ProficiencyLevel.Expert]: 5,
}