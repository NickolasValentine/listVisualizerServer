package data;

public class Fraction {
    public long whole;
    public long num;
    public long den;
    public Fraction(long w, long n, long d) {
        this.whole = w; this.num = n; this.den = d; normalize();
    }
    public Fraction() { this(0,0,1); }
    private void normalize() {
        if (den == 0) den = 1;
        if (den < 0) { den = -den; num = -num; }
        if (num < 0 && whole > 0) {
            whole -= 1;
            num = den - (-num % den);
        } else if (num >= den) {
            long add = num / den;
            whole += add;
            num = num % den;
        }
        if (whole < 0 && num > 0) num = -num;
        long g = gcd(Math.abs(num), Math.abs(den));
        if (g != 0) { num /= g; den /= g; }
    }
    private static long gcd(long a, long b) {
        if (a == 0) return b;
        if (b == 0) return a;
        while (b != 0) {
            long t = b; b = a % b; a = t;
        }
        return Math.abs(a);
    }
    public double toDouble() {
        double w = whole;
        double n = num;
        double d = den;
        return w + (n / d);
    }
    public String toString() {
        if (num == 0) return Long.toString(whole);
        if (whole == 0) return num + "/" + den;
        return whole + " " + Math.abs(num) + "/" + den;
    }
}
