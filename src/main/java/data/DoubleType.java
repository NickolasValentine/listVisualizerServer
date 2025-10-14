package data;

import java.io.*;

public class DoubleType implements UserType {
    public String typeName() { return "Double"; }
    public Object create() { return Double.valueOf(0.0); }
    public Object cloneObject(Object obj) {
        if (obj == null) return create();
        return Double.valueOf(((Double)obj).doubleValue());
    }
    public Object readValue(InputStreamReader in) throws IOException {
        BufferedReader br = new BufferedReader(in);
        String s = br.readLine();
        return parseValue(s);
    }
    public Object parseValue(String ss) {
        if (ss == null || ss.trim().length() == 0) return Double.valueOf(0.0);
        return Double.valueOf(Double.parseDouble(ss.trim()));
    }
    public Comparator getTypeComparator() {
        return new Comparator() {
            public int compare(Object o1, Object o2) {
                Double a = (Double)o1; Double b = (Double)o2;
                return Double.compare(a, b);
            }
        };
    }
    public String serialize(Object obj) { return obj == null ? "" : obj.toString(); }
    public Object deserialize(String s) { return parseValue(s); }
    public String toString() { return typeName(); }
}
