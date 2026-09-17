// The 9 fixed sections of the standard CV template. Values match the backend JSON keys
export enum CvSectionKey {
    PersonalInfo = 'personal_info',
    CareerObjective = 'career_objective',
    Skills = 'skills',
    Experience = 'experience',
    Education = 'education',
    Certifications = 'certifications',
    Projects = 'projects',
    Languages = 'languages',
    AdditionalInfo = 'additional_info',
}

export const CV_SECTION_LABELS: Record<CvSectionKey, string> = {
    [CvSectionKey.PersonalInfo]: 'Personal info',
    [CvSectionKey.CareerObjective]: 'Career objective',
    [CvSectionKey.Skills]: 'Skills',
    [CvSectionKey.Experience]: 'Experience',
    [CvSectionKey.Education]: 'Education',
    [CvSectionKey.Certifications]: 'Certifications',
    [CvSectionKey.Projects]: 'Projects',
    [CvSectionKey.Languages]: 'Languages',
    [CvSectionKey.AdditionalInfo]: 'Additional info',
};