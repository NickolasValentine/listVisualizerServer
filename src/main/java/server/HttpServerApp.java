package server;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import data.UserType;
import data.IntegerType;
import data.DoubleType;
import data.StringType;
import data.FractionType;
import data.UserFactory;
import list.SingleLinkedList;

import com.sun.net.httpserver.*;

import java.net.InetSocketAddress;

public class HttpServerApp {
    private static final UserFactory factory = new UserFactory();
    // current list and prototype (single-list server)
    private static SingleLinkedList currentList = null;
    private static UserType currentPrototype = null;
    // Блокировка для защиты списка и прототипа
    private static final Object listLock = new Object();
    public static void start() throws Exception {
        int port = 5865;
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", port);
        HttpServer server = HttpServer.create(addr, 0);
        System.out.println("Backend starting on http://127.0.0.1:" + port);

        server.createContext("/health", HttpServerApp::handleHealth);        // GET health
        server.createContext("/types", HttpServerApp::handleTypes);          // GET types
        server.createContext("/list", HttpServerApp::handleListRoot);        // POST list create
        server.createContext("/list/items", HttpServerApp::handleListItems); // GET items
        server.createContext("/list/add", HttpServerApp::handleAdd);         // POST add
        server.createContext("/list/insert", HttpServerApp::handleInsert);   // POST insert
        server.createContext("/list/remove", HttpServerApp::handleRemove);   // POST remove
        server.createContext("/list/get", HttpServerApp::handleGet);         // GET get?index=
        server.createContext("/list/find", HttpServerApp::handleFind);       // POST find
        server.createContext("/list/sort", HttpServerApp::handleSort);       // POST sort
        server.createContext("/list/save", HttpServerApp::handleSave);       // POST save {filename, format}
        server.createContext("/list/load", HttpServerApp::handleLoad);       // POST load {filename, format}

        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("Ready.");
    }
    // Небольшие помощники для чтения тела запроса
    private static String readBody(HttpExchange ex) throws IOException {
        InputStream is = ex.getRequestBody();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = is.read(buf)) > 0) baos.write(buf,0,r);
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        OutputStream os = ex.getResponseBody();
        os.write(b);
        os.close();
    }

    private static void sendError(HttpExchange ex, int code, String msg) throws IOException {
        String json = "{\"error\":\"" + jsonEscape(msg) + "\"}";
        sendJson(ex, code, json);
    }

    // Handlers
    private static void handleHealth(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        sendJson(ex, 200, "{\"ok\":true}");
    }

    private static void handleTypes(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        ArrayList<String> names = factory.getTypeNameList();
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (String s : names) {
            if (!first) sb.append(",");
            sb.append("\"").append(jsonEscape(s)).append("\"");
            first = false;
        }
        sb.append("]");
        sendJson(ex, 200, sb.toString());
    }

    private static void handleListRoot(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        String body = readBody(ex);
        String type = extractJsonField(body, "type");
        if (type == null) { sendError(ex, 400, "no type"); return; }
        UserType ut = factory.getBuilderByName(type);
        if (ut == null) { sendError(ex, 400, "unknown type: " + type); return; }
        synchronized (listLock) {
            currentPrototype = ut;
            currentList = new SingleLinkedList(ut);
        }
        sendJson(ex, 200, "{\"ok\":true}");
    }

    private static void handleListItems(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        SingleLinkedList list;
        UserType proto;
        synchronized (listLock) { list = currentList; proto = currentPrototype; }
        if (list == null || proto == null) { sendError(ex, 400, "list not initialized"); return; }
        ArrayList<Object> items = list.toArrayList();
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (Object o : items) {
            String s = proto.serialize(o);
            if (!first) sb.append(",");
            sb.append("\"").append(jsonEscape(s)).append("\"");
            first = false;
        }
        sb.append("]");
        sendJson(ex, 200, sb.toString());
    }

    private static void handleAdd(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        String body = readBody(ex);
        String value = extractJsonField(body, "value");

        synchronized (listLock) {
            if (currentList == null || currentPrototype == null) {
                sendError(ex, 400, "list not initialized");
                return;
            }

            Object obj;
            try {
                // безопасно пробуем десериализовать / распарсить входную строку
                obj = currentPrototype.deserialize(value == null ? "" : value);
            } catch (Exception parseEx) {
                // возвращаем понятную ошибку клиенту
                String typeName = (currentPrototype != null) ? currentPrototype.typeName() : "unknown";
                String msg = "Invalid value for type \"" + typeName + "\": " + (parseEx.getMessage() == null ? "" : parseEx.getMessage());
                sendError(ex, 400, msg);
                return;
            }

            try {
                currentList.add(obj);
                sendJson(ex, 200, "{\"ok\":true}");
            } catch (IllegalArgumentException ia) {
                // для safety: если список отвергает объект по типу/проверке
                sendError(ex, 400, ia.getMessage());
            } catch (Exception any) {
                // защита от непредвиденных ошибок
                sendError(ex, 500, "Internal error on add: " + any.getMessage());
            }
        }
    }


    private static void handleInsert(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        String body = readBody(ex);
        String sval = extractJsonField(body, "value");
        String sidx = extractJsonField(body, "index");

        // сначала парсим индекс и проверяем формат
        int idx;
        if (sidx == null || sidx.trim().isEmpty()) {
            sendError(ex, 400, "Index is required");
            return;
        }
        try {
            idx = Integer.parseInt(sidx.trim());
        } catch (NumberFormatException nfe) {
            sendError(ex, 400, "Invalid index: must be integer");
            return;
        }

        synchronized (listLock) {
            if (currentList == null || currentPrototype == null) {
                sendError(ex, 400, "list not initialized");
                return;
            }

            // проверим допустимый диапазон индекса: 0..size (вставка в конец допускается)
            int size = currentList.size();
            if (idx < 0 || idx > size) {
                sendError(ex, 400, "Index out of range: must be between 0 and " + size);
                return;
            }

            Object obj;
            try {
                obj = currentPrototype.deserialize(sval == null ? "" : sval);
            } catch (Exception parseEx) {
                String typeName = (currentPrototype != null) ? currentPrototype.typeName() : "unknown";
                String msg = "Invalid value for type \"" + typeName + "\": " + (parseEx.getMessage() == null ? "" : parseEx.getMessage());
                sendError(ex, 400, msg);
                return;
            }

            try {
                currentList.insert(idx, obj);
                sendJson(ex, 200, "{\"ok\":true}");
            } catch (IndexOutOfBoundsException iob) {
                sendError(ex, 400, "Index out of range: " + iob.getMessage());
            } catch (IllegalArgumentException ia) {
                sendError(ex, 400, ia.getMessage());
            } catch (Exception any) {
                sendError(ex, 500, "Internal error on insert: " + any.getMessage());
            }
        }
    }


    private static void handleRemove(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        String body = readBody(ex);
        String sidx = extractJsonField(body, "index");
        int idx = -1;
        try { idx = Integer.parseInt(sidx == null ? "-1" : sidx); } catch (Exception e) { idx = -1; }
        synchronized (listLock) {
            if (currentList == null || currentPrototype == null) { sendError(ex, 400, "list not initialized"); return; }
            try {
                Object removed = currentList.remove(idx);
                String serialized = currentPrototype.serialize(removed);
                sendJson(ex, 200, "{\"ok\":true, \"removed\":\"" + jsonEscape(serialized) + "\"}");
            } catch (IndexOutOfBoundsException iob) {
                sendError(ex, 400, iob.getMessage());
            }
        }
    }

    private static void handleGet(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        String q = ex.getRequestURI().getQuery();
        Map<String,String> qm = parseQuery(q);
        String sidx = qm.get("index");
        int idx = -1;
        try { idx = Integer.parseInt(sidx == null ? "-1" : sidx); } catch (Exception e) { idx = -1; }
        synchronized (listLock) {
            if (currentList == null || currentPrototype == null) { sendError(ex, 400, "list not initialized"); return; }
            try {
                Object val = currentList.get(idx);
                sendJson(ex, 200, "{\"value\":\"" + jsonEscape(currentPrototype.serialize(val)) + "\"}");
            } catch (IndexOutOfBoundsException iob) {
                sendError(ex, 400, iob.getMessage());
            }
        }
    }

    private static void handleFind(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        String body = readBody(ex);
        String value = extractJsonField(body, "value");
        if (value == null) value = "";

        synchronized (listLock) {
            if (currentList == null || currentPrototype == null) {
                sendError(ex, 400, "list not initialized");
                return;
            }
            ArrayList<Object> items = currentList.toArrayList();
            for (int i = 0; i < items.size(); i++) {
                Object o = items.get(i);
                String s = currentPrototype.serialize(o);
                if (s == null) s = "";
                if (s.equals(value)) {
                    sendJson(ex, 200, "{\"index\":" + i + "}");
                    return;
                }
            }
            // not found
            sendJson(ex, 200, "{\"index\":-1}");
        }
    }

    private static void handleSort(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        synchronized (listLock) {
            if (currentList == null || currentPrototype == null) { sendError(ex, 400, "list not initialized"); return; }
            currentList.sort(currentPrototype.getTypeComparator());
            sendJson(ex, 200, "{\"ok\":true}");
        }
    }

    private static void handleSave(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        String body = readBody(ex);
        String filename = extractJsonField(body, "filename");
        String format = extractJsonField(body, "format");
        if (filename == null || filename.trim().isEmpty()) { sendError(ex, 400, "filename required"); return; }
        filename = sanitizeFilename(filename);
        if (format == null) format = "json";
        synchronized (listLock) {
            if (currentList == null || currentPrototype == null) { sendError(ex, 400, "list not initialized"); return; }
            try {
                if ("bin".equalsIgnoreCase(format)) currentList.saveToBinaryFile(filename);
                else currentList.saveToFile(filename);
                sendJson(ex, 200, "{\"ok\":true}");
            } catch (IOException io) {
                sendError(ex, 500, "IO error: " + io.getMessage());
            }
        }
    }

    private static void handleLoad(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        String body = readBody(ex);
        String filename = extractJsonField(body, "filename");
        String format = extractJsonField(body, "format");
        if (filename == null || filename.trim().isEmpty()) { sendError(ex, 400, "filename required"); return; }

        // Выбираем путь: абсолютный используется, иначе стандартная санитаризация (имя в рабочей директории)
        File inFile;
        File candidate = new File(filename);
        if (candidate.isAbsolute()) {
            inFile = candidate;
        } else {
            inFile = new File(sanitizeFilename(filename));
        }

        if (format == null) format = "json";

        try {
            SingleLinkedList loaded;
            if ("bin".equalsIgnoreCase(format)) {
                if (!inFile.exists()) { sendError(ex, 400, "file not found: " + inFile.getAbsolutePath()); return; }
                loaded = SingleLinkedList.loadFromBinaryFile(inFile.getAbsolutePath(), factory);
            } else {
                if (!inFile.exists()) { sendError(ex, 400, "file not found: " + inFile.getAbsolutePath()); return; }
                loaded = SingleLinkedList.loadFromFile(inFile.getAbsolutePath(), factory);
            }

            synchronized (listLock) {
                currentList = loaded;
                currentPrototype = (loaded != null ? loaded.prototype : null);
            }

            String typeName = (currentPrototype != null) ? currentPrototype.typeName() : "";
            // Возвращаем информацию о типе в ответе
            sendJson(ex, 200, "{\"ok\":true, \"type\":\"" + jsonEscape(typeName) + "\"}");
        } catch (IOException io) {
            sendError(ex, 500, "IO error: " + io.getMessage());
        } catch (SecurityException se) {
            sendError(ex, 500, "Security error: " + se.getMessage());
        }
    }

    // Utilities

    // Базовая защита от path traversal
    private static String sanitizeFilename(String fname) {
        if (fname == null || fname.trim().isEmpty()) {
            return "data.json";
        }
        // Просто используем путь как есть (только убираем опасные символы, если нужно)
        // Для локального приложения — безопасно
        return fname.trim();
    }
    // Извлекает поле из JSON-тела
    private static String extractJsonField(String body, String field) {
        if (body == null) return null;
        String pattern = "\"" + field + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(body);
        if (m.find()) return m.group(1);
        pattern = "\"" + field + "\"\\s*:\\s*(-?\\d+)";
        m = java.util.regex.Pattern.compile(pattern).matcher(body);
        if (m.find()) return m.group(1);
        return null;
    }

    private static Map<String,String> parseQuery(String q) {
        HashMap<String,String> map = new HashMap<>();
        if (q == null) return map;
        for (String part : q.split("&")) {
            String[] kv = part.split("=",2);
            if (kv.length==2) map.put(kv[0], kv[1]);
        }
        return map;
    }

    private static String jsonEscape(String s) {
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
}
