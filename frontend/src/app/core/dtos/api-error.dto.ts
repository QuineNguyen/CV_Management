export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  /** Stable, machine-readable cause. Branch on this, never on `message`. */
  code: string;
  message: string;
  path: string;
  /** Present only on validation failures (HTTP 400). */
  fieldErrors?: ApiFieldError[];
}
 
export interface ApiFieldError {
  field: string;
  message: string;
}