package uzeduai.be.tenant.context;

/**
 * Thread-local holder for {@link TenantContext}.
 * <p>
 * Always cleared at the end of the HTTP request filter. Any future {@code @Async}
 * / executor work must propagate and clear this context via a TaskDecorator —
 * do not assume inheritance across threads.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> HOLDER = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void set(TenantContext context) {
        HOLDER.set(context);
    }

    public static TenantContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
