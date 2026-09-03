import { ApiResponse } from "./api-response.dto";

export interface ErrorDetail {
  timestamp: string;
  status: number;
  error: string;
  path: string;
  // Present only on validation failures (HTTP 400)
  fieldErrors?: ApiFieldError[];
}
 
export interface ApiFieldError {
  field: string;
  message: string;
}

export type ApiErrorResponse = ApiResponse<ErrorDetail>;