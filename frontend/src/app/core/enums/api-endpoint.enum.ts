// Paths appended to environment.apiBaseUrl
export enum ApiEndpoint {
    // Auth routes
    Login = '/auth/login',
    GoogleLogin = '/auth/google',
    Logout = '/auth/logout',
    ChangePassword = '/auth/change-password',

    // Department routes
    Departments = '/departments',
    DepartmentTree = '/departments/tree',
}