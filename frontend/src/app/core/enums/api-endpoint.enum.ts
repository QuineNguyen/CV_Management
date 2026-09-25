// Paths appended to environment.apiBaseUrl
export enum ApiEndpoint {
    // Auth routes
    Login = '/auth/login',
    GoogleLogin = '/auth/google',
    Logout = '/auth/logout',
    ChangePassword = '/auth/change-password',
    ResetPassword = '/admin/users',

    // Department routes
    Departments = '/departments',
    DepartmentTree = '/departments/tree',

    // Team routes
    Team = '/teams',

    // User routes
    User = '/users',

    // Active tech lead options for dropdowns
    TechLead = '/users/tech-leads',

    // Profile routes
    Profiles = '/cv-profiles',
    Employees = '/employees',

    // CV routes
    Cvs = '/cvs',

    // Image routes
    Images = '/images',

    // My profile (self-service)
    MyProfile = '/me/profile',
    MyProfileUpdateRequest = '/me/profile-update-request',

    // Profile update requests (Admin + HR)
    ProfileUpdateRequests = '/profile-update-requests',

    // Approval routes
    Approvals = '/approvals',
    ApprovalQueue = '/approvals/queue',

    // Notification routes
    Notifications = '/notifications',
    NotificationsUnreadCount = '/notifications/unread-count',
    NotificationsReadAll = '/notifications/read-all',
}