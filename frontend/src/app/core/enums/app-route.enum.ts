// Absolute paths used for navigation. Keeps route strings out of components
export enum AppRoute {
    // Auth routes
    Login = 'login',
    ChangePassword = 'account/change-password',
    
    // Core routes
    Home = 'home',
    Users = 'users',
    Departments = 'departments',
    Teams = 'teams',
    Profiles = 'cv-profiles',
    Cvs = 'cvs',
    CvsNew = 'cvs/new',
    CvsDeleted = 'cvs/deleted',
    MyProfile = 'my-profile',
    ProfileUpdateRequests = 'profile-update-requests',
    Approvals = 'approvals',
    ApprovalQueue = 'approvals/queue',
}