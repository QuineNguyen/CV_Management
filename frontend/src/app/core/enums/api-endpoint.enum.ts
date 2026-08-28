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
}