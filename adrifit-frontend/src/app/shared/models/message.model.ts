export type SenderRole = 'TRAINER' | 'CLIENT';

export interface ChatMessage {
  id: number;
  clientId: number;
  senderRole: SenderRole;
  content: string;
  createdAt: string;
  readAt: string | null;
}

export interface MyConversation {
  enabled: boolean;
  planName: string | null;
  messages: ChatMessage[];
}

export interface ConversationSummary {
  clientId: number;
  clientName: string;
  photoBase64: string | null;
  planName: string | null;
  messagingEnabled: boolean;
  prioritySupport: boolean;
  lastMessage: string | null;
  lastMessageFrom: SenderRole | null;
  lastMessageAt: string | null;
  unreadCount: number;
}

export interface Conversation {
  clientId: number;
  clientName: string;
  messagingEnabled: boolean;
  planName: string | null;
  messages: ChatMessage[];
}
