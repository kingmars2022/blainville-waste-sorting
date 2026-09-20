import { api, ApiError } from "./client";
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

const PROCESSING_RETRIES = 3;
const FALLBACK_RETRY_SECONDS = 2;

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Asks for the answer, retrying while the photo is still being prepared.
 *
 * The upload lands in storage and a Lambda processes it asynchronously, so
 * there is a gap between "uploaded" and "ready" that nobody can remove — only
 * wait out. The server waits too, but it holds a request thread to do it, so
 * it gives up first and says so.
 *
 * Keyed on Retry-After rather than on the status, because this endpoint has a
 * second 503 that means "no API key is configured" — retrying that one just
 * hammers a misconfiguration.
 */
async function identify(photoId: string, language: Language) {
  for (let attempt = 0; ; attempt++) {
    try {
      return await api.post<PhotoAnswer>(`/photos/${photoId}/identify?language=${language}`);
    } catch (error) {
      const askedToRetry = error instanceof ApiError && error.retryAfter !== null;
      if (!askedToRetry || attempt >= PROCESSING_RETRIES) {
        throw error;
      }
      await wait(((error as ApiError).retryAfter ?? FALLBACK_RETRY_SECONDS) * 1000);
    }
  }
}

/**
 * Ticket, upload, then identify — the three steps, in order.
 *
 * @param onUploaded called once the bytes are in storage, so the caller can
 *   change what the button says; identification is the slower half and looked
 *   like a stalled upload without it
 */
export async function askAboutPhoto(file: File, language: Language, onUploaded?: () => void) {
  const ticket = await requestUploadUrl(file);
  await uploadToStorage(ticket, file);
  onUploaded?.();
  return { photoId: ticket.photoId, answer: await identify(ticket.photoId, language) };
}
