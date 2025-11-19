package API_BoPhieu.constants;

public enum EventManagement {
    STAFF, MANAGE;

    public boolean atLeast(EventManagement requiredRole) {
        if (requiredRole == null) {
            return false;
        }
        return this.ordinal() >= requiredRole.ordinal();
    }
}
