package data;

import java.io.*;

public class IntegerType implements UserType {
    public String typeName() { return "Integer"; }
    public Object create() { return Integer.valueOf(0); }
    public Object cloneObject(Object obj) {
        if (obj == null) return create();
        return Integer.valueOf(((Integer)obj).intValue());
    }
    public Object readValue(InputStreamReader in) throws IOException {
        BufferedReader br = new BufferedReader(in);
        String s = br.readLine();
        return parseValue(s);
    }
    public Object parseValue(String ss) {
        if (ss == null || ss.trim().length() == 0) return Integer.valueOf(0);
        return Integer.valueOf(Integer.parseInt(ss.trim()));
    }
    public Comparator getTypeComparator() {
        return new Comparator() {
            public int compare(Object o1, Object o2) {
                Integer a = (Integer)o1; Integer b = (Integer)o2;
                return Integer.compare(a, b);
            }
        };
    }
    public String serialize(Object obj) { return obj == null ? "" : obj.toString(); }
    public Object deserialize(String s) { return parseValue(s); }
    public String toString() { return typeName(); }
}
