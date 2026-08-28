// A team the employee belongs to; the only teams a profile may link to.
export interface EmployeeTeamResponse {
    id: string;
    code: string;
    name: string;
    primaryTeam: boolean;
}