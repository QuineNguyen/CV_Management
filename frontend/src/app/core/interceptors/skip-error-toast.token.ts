import { HttpContextToken } from "@angular/common/http";

// Set on background requests whose failure the user did not cause and cannot act on.
export const SKIP_ERROR_TOAST = new HttpContextToken<boolean>(() => false);