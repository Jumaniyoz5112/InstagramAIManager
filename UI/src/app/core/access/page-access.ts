import { PageAccess, UserResponse } from '../auth/auth.models';

export function hasPage(user: UserResponse | null | undefined, code: string): boolean {
  if (!user) {
    return false;
  }
  return (user.pages ?? []).some((page) => page.code === code);
}

export function firstAllowedPath(user: UserResponse | null | undefined): string {
  const pages = [...(user?.pages ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
  return pages[0]?.path ?? '/app/pending';
}

export function navPages(user: UserResponse | null | undefined): PageAccess[] {
  return [...(user?.pages ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
}

export function isPlatformAdmin(user: UserResponse | null | undefined): boolean {
  return !!user?.platformRoles?.includes('PLATFORM_ADMIN');
}
