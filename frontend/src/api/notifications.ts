import { api } from "./client";

export interface ResidentNotification {
  id: number;
  noticeId: number;
  titleFr: string;
  titleEn: string;
  titleZh: string;
  bodyFr: string;
  bodyEn: string;
  bodyZh: string;
  createdAt: string;
  readAt: string | null;
}

export function fetchNotifications() {
  return api.get<ResidentNotification[]>("/notifications");
}

/** Drives the badge without pulling the whole inbox to draw a number. */
export function fetchUnreadCount() {
  return api.get<{ unread: number }>("/notifications/unread-count");
}

export function markNotificationRead(id: number) {
  return api.post<{ unread: number }>(`/notifications/${id}/read`);
}
