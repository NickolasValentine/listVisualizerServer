package data;

import java.io.*;

public class StringType implements UserType {
    public String typeName() { return "String"; }
    public Object create() { return new String(""); }
    public Object cloneObject(Object obj) {
        if (obj == null) return create();
        return new String((String)obj);
    }
    public Object readValue(InputStreamReader in) throws IOException {
        BufferedReader br = new BufferedReader(in);
        String s = br.readLine();
        return s;
    }
    public Object parseValue(String ss) {
        return ss == null ? "" : ss;
    }
    public Comparator getTypeComparator() {
        return new Comparator() {
            public int compare(Object o1, Object o2) {
                String a = (String)o1; String b = (String)o2;
                return a.compareTo(b);
            }
        };
    }
    public String serialize(Object obj) { return obj == null ? "" : obj.toString(); }
    public Object deserialize(String s) { return s; }
    public String toString() { return typeName(); }
}
