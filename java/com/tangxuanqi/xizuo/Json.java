package com.tangxuanqi.xizuo;

import java.util.ArrayList;
import java.util.List;

/** 极简 JSON 处理：只解析网页端传来的 [{id,at,title,desc,kind}] 数组，避免引入第三方库 */
public final class Json {
    private Json() {}

    public static List<AlarmItem> parseItems(String s) {
        List<AlarmItem> out = new ArrayList<>();
        if (s == null) return out;
        int i = 0, n = s.length();
        while (i < n) {
            int o = s.indexOf('{', i);
            if (o < 0) break;
            int c = s.indexOf('}', o);
            if (c < 0) break;
            String body = s.substring(o + 1, c);
            AlarmItem it = new AlarmItem();
            for (String part : splitTop(body)) {
                int col = part.indexOf(':');
                if (col < 0) continue;
                String k = unq(part.substring(0, col).trim());
                String v = part.substring(col + 1).trim();
                if (k.equals("id")) it.id = unq(v);
                else if (k.equals("title")) it.title = unq(v);
                else if (k.equals("desc")) it.desc = unq(v);
                else if (k.equals("at")) it.at = parseLong(v);
                else if (k.equals("kind")) it.kind = (int) parseLong(v);
            }
            if (it.at > 0 && it.title.length() > 0) out.add(it);
            i = c + 1;
        }
        return out;
    }

    /** 按顶层逗号切分（忽略字符串内的逗号） */
    private static List<String> splitTop(String s) {
        List<String> out = new ArrayList<>();
        boolean inStr = false, esc = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (esc) { cur.append(ch); esc = false; continue; }
            if (ch == '\\') { cur.append(ch); esc = true; continue; }
            if (ch == '"') { inStr = !inStr; cur.append(ch); continue; }
            if (ch == ',' && !inStr) { out.add(cur.toString()); cur.setLength(0); continue; }
            cur.append(ch);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static String unq(String v) {
        v = v.trim();
        if (v.length() >= 2 && v.charAt(0) == '"' && v.charAt(v.length() - 1) == '"') {
            v = v.substring(1, v.length() - 1);
        }
        return v.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static long parseLong(String v) {
        try { return Long.parseLong(v.trim()); } catch (Exception e) { return 0L; }
    }

    public static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(' ');
                    else b.append(c);
            }
        }
        return b.toString();
    }
}
