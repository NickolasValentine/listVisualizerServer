package data;

import java.io.*;

public class FractionType implements UserType {
    public String typeName() { return "Fraction"; }
    public Object create() { return new Fraction(0,0,1); }
    public Object cloneObject(Object obj) { if (obj == null) return create(); Fraction f = (Fraction)obj; return new Fraction(f.whole, f.num, f.den); }
    public Object readValue(InputStreamReader in) throws IOException {
        BufferedReader br = new BufferedReader(in);
        String s = br.readLine();
        return parseValue(s);
    }
    public Object parseValue(String ss) {
        if (ss == null || ss.trim().isEmpty()) return new Fraction();
        String s = ss.trim();
        try {
            if (s.contains(" ")) {
                String[] parts = s.split("\\s+");
                long w = Long.parseLong(parts[0]);
                String fr = parts[1];
                String[] nd = fr.split("/");
                long n = Long.parseLong(nd[0]);
                long d = Long.parseLong(nd[1]);
                return new Fraction(w,n,d);
            } else if (s.contains("/")) {
                String[] nd = s.split("/");
                long n = Long.parseLong(nd[0]);
                long d = Long.parseLong(nd[1]);
                return new Fraction(0,n,d);
            } else {
                long w = Long.parseLong(s);
                return new Fraction(w,0,1);
            }
        } catch (Exception ex) {
            return new Fraction();
        }
    }
    public Comparator getTypeComparator() {
        return new Comparator() {
            public int compare(Object o1, Object o2) {
                Fraction a = (Fraction)o1;
                Fraction b = (Fraction)o2;
                long anum = a.whole * a.den + a.num;
                long aden = a.den;
                long bnum = b.whole * b.den + b.num;
                long bden = b.den;
                try {
                    long left = anum * bden;
                    long right = bnum * aden;
                    return Long.compare(left, right);
                } catch (ArithmeticException ex) {
                    double da = a.toDouble();
                    double db = b.toDouble();
                    return Double.compare(da, db);
                }
            }
        };
    }
    public String serialize(Object obj) { return obj == null ? "" : obj.toString(); }
    public Object deserialize(String s) { return parseValue(s); }
    public String toString() { return typeName(); }
}
