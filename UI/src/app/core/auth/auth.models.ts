export interface ApiResponse<T> {
  success: boolean;
  data: T;
  messageKey: string | null;
  messageArgs?: string[] | null;
  timestamp: string;
}

export interface PageAccess {
  code: string;
  name: string;
  description: string | null;
  path: string;
  sortOrder: number;
  adminOnly: boolean;
}

export interface UserResponse {
  id: string;
  email: string;
  fullName: string;
  platformRoles: string[];
  activeTenantId: string | null;
  activeWorkspaceId: string | null;
  activeTenantRole: 'OWNER' | 'ADMIN' | 'MEMBER' | null;
  tenants: TenantSummary[];
  workspaces: WorkspaceSummary[];
  pages: PageAccess[];
}

export interface TenantSummary {
  id: string;
  name: string;
  slug: string;
  role: 'OWNER' | 'ADMIN' | 'MEMBER';
}

export interface WorkspaceSummary {
  id: string;
  tenantId: string;
  name: string;
}

export interface AuthTokensResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: UserResponse;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  tenantName: string;
  tenantSlug: string;
}
