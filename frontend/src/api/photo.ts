import { api } from "./client";
import type { AssistantSource } from "./assistant";
import type { Language } from "../i18n/messages";

export interface UploadTicket {
  photoId: string;
  uploadUrl: string;
  expiresIn: number;
}

export interface PhotoAnswer {
  /** What the vision model thought it saw, or null if it could not tell. */
  identifiedAs: string | null;
  /** True only when the sorting guide covered it. The bin never comes from the model. */
  grounded: boolean;
  answer: string;
  provider: string;
  sources: AssistantSource[];
}

function requestUploadUrl(file: File) {
  return api.post<UploadTicket>("/photos/upload-url", {
    contentType: file.type,
    contentLength: file.size
  });
}

/**
 * Uploads the photo straight to S3.
 *
 * <p>Deliberately a bare `fetch` rather than the shared API client: this
 * request does not go to our backend at all, so it must not carry the
 * Authorization header the client attaches. Sending a resident's JWT to a
 * storage provider would be a credential leak with no upside.
 */
async function uploadToStorage(ticket: UploadTicket, file: File) {
  const response = await fetch(ticket.uploadUrl, {
    method: "PUT",
    headers: { "Content-Type": file.type },
    body: file
  });

  if (!response.ok) {
    throw new Error(`Upload failed with status ${response.status}`);
  }
}

function identify(photoId: string, language: Language) {
  return api.post<PhotoAnswer>(
    `/photos/${photoId}/identify?language=${language}`
  );
}

/** Ticket, upload, then identify — the three steps, in order. */
export async function askAboutPhoto(file: File, language: Language) {
  const ticket = await requestUploadUrl(file);
  await uploadToStorage(ticket, file);
  return { photoId: ticket.photoId, answer: await identify(ticket.photoId, language) };
}
