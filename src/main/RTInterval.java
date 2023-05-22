package main;

public class RTInterval {

    private long seconds;
    private int quotient = 0;

    public RTInterval(long seconds) {
        this.seconds = seconds;
    }

    public boolean get(long time) {
        if (time / seconds > quotient) {
            quotient++;
            return true;
        }
        return false;
    }

    public void reset() {
        quotient = 0;
    }

}
