/*
 * What the avatar picker hands back. Both halves travel together: the id is what gets saved, the
 * URL is what the screen shows until the next load returns a fresh one.
 */
export interface AvatarChange {
    imageId: string;
    presignedUrl: string;
}