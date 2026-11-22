package cl.farus.farusfly;

/** Pure-Java offline image-plane follower; NEVER sends commands to aircraft. */
public final class FollowSimulator {
    public static final class Result {
        public final double yaw, forward;
        public final String state;
        Result(double yaw, double forward, String state) { this.yaw=yaw; this.forward=forward; this.state=state; }
    }
    private long lastSeenMs = -1;
    private double filteredX = 0.5;
    public Result update(double centerX, double relativeSize, boolean targetVisible, long nowMs) {
        if (!targetVisible || !Double.isFinite(centerX) || !Double.isFinite(relativeSize)
                || centerX < 0 || centerX > 1 || relativeSize <= 0 || relativeSize > 1) {
            return new Result(0, 0, "SIN OBJETIVO — CONTROL BLOQUEADO");
        }
        if (lastSeenMs >= 0 && (nowMs < lastSeenMs || nowMs-lastSeenMs > 1000)) filteredX=centerX;
        else filteredX=0.65*filteredX+0.35*centerX;
        lastSeenMs=nowMs;
        // Relative size is not a distance measurement: forward flight intentionally disabled.
        double yaw=Math.max(-0.25,Math.min(0.25,(filteredX-0.5)*0.5));
        return new Result(yaw,0,"SIMULACIÓN VISUAL — SIN MOVIMIENTO REAL");
    }
}
