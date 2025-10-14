package data;

import java.io.*;

public interface UserType {
    String typeName();
    Object create();
    Object cloneObject(Object obj);
    Object readValue(InputStreamReader in) throws IOException;
    Object parseValue(String ss);
    Comparator getTypeComparator();
    String serialize(Object obj);
    Object deserialize(String s);
}
