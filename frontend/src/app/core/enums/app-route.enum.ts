// Absolute paths used for navigation. Keeps route strings out of components
export enum AppRoute {
    // Auth routes
    Login = 'login',
    ChangePassword = 'account/change-password',
    
    // Core routes
    Dashboard = 'dashboard',
    Users = 'users',
    Departments = 'departments',
    Teams = 'teams',
    Profiles = 'cv-profiles',
    Cvs = 'cvs',
    CvsNew = 'cvs/new',
    CvsDeleted = 'cvs/deleted',
    CvsSearch = 'cvs/search',
    Skills = 'settings/skills',
    MyProfile = 'my-profile',
    ProfileUpdateRequests = 'profile-update-requests',
    Approvals = 'approvals',
    ApprovalQueue = 'approvals/queue',
    PendingDrafts = 'approvals/pending',
    Notifications = 'notifications',
    UpdateRequests = 'update-requests',
    BatchRequests = 'batch-requests',
    BatchCreate = 'batch-requests/new',
}