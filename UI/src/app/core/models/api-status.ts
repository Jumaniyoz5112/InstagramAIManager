export interface ApiStatus {
  status: string;
  service: string;
  timestamp: string;
  details: Record<string, string>;
}
