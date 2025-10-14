package list;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import data.UserType;
import data.UserFactory;
import data.IntegerType;
import data.DoubleType;
import data.StringType;
import data.FractionType;
import data.Fraction;
import data.Comparator;
import data.DoWith;
import data.TestIt;
import list.SingleLinkedList;

// Хранит объекты одного типа
public class SingleLinkedList {
    private class Node {
        Object value;
        Node next;
        Node(Object v) { value = v; next = null; }
    }

    private Node head;
    private int size;
    public final UserType prototype;
    private final Class<?> elementClass;

    public SingleLinkedList(UserType prototype) {
        this.prototype = prototype;
        this.head = null;
        this.size = 0;
        Object sample = prototype.create();
        if (sample != null) elementClass = sample.getClass();
        else elementClass = null;
    }

    private void checkAcceptable(Object obj) {
        if (obj == null) return;
        if (elementClass != null && !elementClass.isAssignableFrom(obj.getClass())) {
            throw new IllegalArgumentException("Object of class " + obj.getClass()
                    + " is not acceptable for list of type " + prototype.typeName()
                    + " (expected " + elementClass + ")");
        }
    }

    public int size() { return size; }

    public void add(Object obj) {
        checkAcceptable(obj);
        Node n = new Node(obj);
        if (head == null) head = n;
        else {
            Node cur = head;
            while (cur.next != null) cur = cur.next;
            cur.next = n;
        }
        size++;
    }

    public Object get(int index) {
        checkIndex(index);
        Node cur = head;
        for (int i = 0; i < index; i++) cur = cur.next;
        return cur.value;
    }

    public void insert(int index, Object obj) {
        if (index < 0 || index > size) throw new IndexOutOfBoundsException("index=" + index);
        checkAcceptable(obj);
        Node n = new Node(obj);
        if (index == 0) {
            n.next = head;
            head = n;
        } else {
            Node cur = head;
            for (int i = 0; i < index - 1; i++) cur = cur.next;
            n.next = cur.next;
            cur.next = n;
        }
        size++;
    }

    public Object remove(int index) {
        checkIndex(index);
        Object removed;
        if (index == 0) {
            removed = head.value;
            head = head.next;
        } else {
            Node cur = head;
            for (int i = 0; i < index - 1; i++) cur = cur.next;
            removed = cur.next.value;
            cur.next = cur.next.next;
        }
        size--;
        return removed;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
    }

    public void forEach(DoWith action) {
        Node cur = head;
        while (cur != null) {
            action.doWith(cur.value);
            cur = cur.next;
        }
    }

    public Object firstThat(TestIt test) {
        Node cur = head;
        while (cur != null) {
            if (test.testIt(cur.value)) return cur.value;
            cur = cur.next;
        }
        return null;
    }

    public void sort(Comparator comp) {
        if (size <= 1) return;
        Object[] arr = new Object[size];
        Node cur = head;
        for (int i = 0; i < size; i++) {
            arr[i] = cur.value;
            cur = cur.next;
        }
        quickSort(arr, 0, arr.length - 1, comp);
        cur = head;
        for (int i = 0; i < size; i++) {
            cur.value = arr[i];
            cur = cur.next;
        }
    }

    private void quickSort(Object[] a, int lo, int hi, Comparator comp) {
        if (lo >= hi) return;
        int p = partition(a, lo, hi, comp);
        quickSort(a, lo, p - 1, comp);
        quickSort(a, p + 1, hi, comp);
    }

    private int partition(Object[] a, int lo, int hi, Comparator comp) {
        Object pivot = a[hi];
        int i = lo - 1;
        for (int j = lo; j < hi; j++) {
            if (comp.compare(a[j], pivot) <= 0) {
                i++;
                Object tmp = a[i]; a[i] = a[j]; a[j] = tmp;
            }
        }
        Object tmp = a[i+1]; a[i+1] = a[hi]; a[hi] = tmp;
        return i+1;
    }

    public void saveToFile(String filename) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename))) {
            bw.write("{");
            bw.newLine();
            bw.write("  \"type\": \"" + jsonEscape(prototype.typeName()) + "\",");
            bw.newLine();
            bw.write("  \"items\": [");
            bw.newLine();
            Node cur = head;
            boolean first = true;
            while (cur != null) {
                String sval = prototype.serialize(cur.value);
                String escaped = jsonEscape(sval);
                if (!first) bw.write(",");
                bw.write("    \"" + escaped + "\"");
                bw.newLine();
                first = false;
                cur = cur.next;
            }
            bw.write("  ]");
            bw.newLine();
            bw.write("}");
        }
    }

    public static SingleLinkedList loadFromFile(String filename, UserFactory factory) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String ln;
            while ((ln = br.readLine()) != null) { sb.append(ln); sb.append('\n'); }
        }
        String content = sb.toString();
        Pattern pType = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
        Matcher mType = pType.matcher(content);
        if (!mType.find()) throw new IOException("Invalid JSON: type not found");
        String tname = unescapeJson(mType.group(1));
        UserType builder = factory.getBuilderByName(tname);
        if (builder == null) throw new IOException("Unknown type in file: " + tname);

        Pattern pItems = Pattern.compile("\"items\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher mItems = pItems.matcher(content);
        ArrayList<String> items = new ArrayList<>();
        if (mItems.find()) {
            String itemsContent = mItems.group(1);
            items = parseJsonStringList(itemsContent);
        } else {
            throw new IOException("Invalid JSON: items array not found");
        }

        SingleLinkedList lst = new SingleLinkedList(builder);
        for (String s : items) {
            String un = unescapeJson(s);
            Object obj = builder.deserialize(un);
            lst.add(obj);
        }
        return lst;
    }

    public void saveToBinaryFile(String filename) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(filename)))) {
            dos.writeUTF(prototype.typeName());
            dos.writeInt(size);
            Node cur = head;
            while (cur != null) {
                if (cur.value == null) {
                    dos.writeBoolean(true);
                } else {
                    dos.writeBoolean(false);
                    dos.writeUTF(prototype.serialize(cur.value));
                }
                cur = cur.next;
            }
            dos.flush();
        }
    }

    public static SingleLinkedList loadFromBinaryFile(String filename, UserFactory factory) throws IOException {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(filename)))) {
            String tname = dis.readUTF();
            UserType builder = factory.getBuilderByName(tname);
            if (builder == null) throw new IOException("Unknown type in file: " + tname);
            int n = dis.readInt();
            SingleLinkedList lst = new SingleLinkedList(builder);
            for (int i = 0; i < n; i++) {
                boolean isNull = dis.readBoolean();
                if (isNull) {
                    lst.add(null);
                } else {
                    String s = dis.readUTF();
                    Object obj = builder.deserialize(s);
                    lst.add(obj);
                }
            }
            return lst;
        }
    }

    private String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\"': sb.append("\\\""); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20 || c > 0x7E) {
                        sb.append(String.format("\\u%04x", (int)c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static String unescapeJson(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char nxt = s.charAt(i + 1);
                switch (nxt) {
                    case '\\': sb.append('\\'); i++; break;
                    case '\"': sb.append('\"'); i++; break;
                    case '/': sb.append('/'); i++; break;
                    case 'b': sb.append('\b'); i++; break;
                    case 'f': sb.append('\f'); i++; break;
                    case 'n': sb.append('\n'); i++; break;
                    case 'r': sb.append('\r'); i++; break;
                    case 't': sb.append('\t'); i++; break;
                    case 'u':
                        if (i + 5 < s.length()) {
                            String hx = s.substring(i+2, i+6);
                            try {
                                int code = Integer.parseInt(hx, 16);
                                sb.append((char)code);
                                i += 5;
                            } catch (NumberFormatException ex) {
                                sb.append('\\'); sb.append('u');
                                i++;
                            }
                        } else {
                            sb.append('\\');
                        }
                        break;
                    default:
                        sb.append(nxt); i++; break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static ArrayList<String> parseJsonStringList(String s) {
        ArrayList<String> res = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == ',') { i++; continue; }
            if (c == '"') {
                StringBuilder cur = new StringBuilder();
                i++;
                while (i < n) {
                    char ch = s.charAt(i);
                    if (ch == '\\' && i + 1 < n) {
                        char next = s.charAt(i+1);
                        cur.append('\\').append(next);
                        i += 2;
                    } else if (ch == '"') {
                        i++;
                        break;
                    } else {
                        cur.append(ch);
                        i++;
                    }
                }
                res.add(cur.toString());
            } else {
                int start = i;
                while (i < n && s.charAt(i) != ',') i++;
                String token = s.substring(start, i).trim();
                if (!token.isEmpty()) res.add(token);
            }
        }
        return res;
    }

    public ArrayList<Object> toArrayList() {
        ArrayList<Object> res = new ArrayList<>();
        Node cur = head;
        while (cur != null) { res.add(cur.value); cur = cur.next; }
        return res;
    }

    public void printList() {
        System.out.print("[");
        Node cur = head;
        boolean first = true;
        while (cur != null) {
            if (!first) System.out.print(", ");
            System.out.print(cur.value);
            first = false;
            cur = cur.next;
        }
        System.out.println("]");
    }
}
