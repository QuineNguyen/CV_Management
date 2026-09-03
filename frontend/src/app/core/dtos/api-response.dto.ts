export interface ApiResponse<T> {
    // SUCCESS on 2xx, an ErrorCode name otherwise. Branch on this, never on message.
    code: string;
    message: string;
    data: T;
}