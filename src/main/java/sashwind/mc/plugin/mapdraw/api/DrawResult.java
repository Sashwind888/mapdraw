package sashwind.mc.plugin.mapdraw.api;

public class DrawResult {

    private final boolean success;
    private final String message;
    private final Status status;

    public enum Status {
        SUCCESS,
        NO_PERMISSION,
        CANVAS_PROTECTED,
        CANVAS_NOT_FOUND,
        INSUFFICIENT_FUNDS,
        CANNOT_SHRINK,
        INVALID_ARGUMENTS,
        ERROR
    }

    private DrawResult(boolean success, Status status, String message) {
        this.success = success;
        this.status = status;
        this.message = message;
    }

    public static DrawResult success(String message) {
        return new DrawResult(true, Status.SUCCESS, message);
    }

    public static DrawResult success() {
        return new DrawResult(true, Status.SUCCESS, null);
    }

    public static DrawResult failure(Status status, String message) {
        return new DrawResult(false, status, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Status getStatus() {
        return status;
    }
}
