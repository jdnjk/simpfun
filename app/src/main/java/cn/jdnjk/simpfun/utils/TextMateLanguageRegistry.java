package cn.jdnjk.simpfun.utils;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TextMateLanguageRegistry {

    public static final String LANGUAGES_ASSET_PATH = "editor/textmate/languages.json";

    public static final class Language {
        public final String name;
        public final String scopeName;
        public final List<String> extensions;

        Language(String name, String scopeName, List<String> extensions) {
            this.name = name;
            this.scopeName = scopeName;
            this.extensions = extensions;
        }
    }

    private static volatile List<Language> cached;

    private TextMateLanguageRegistry() {}

    public static List<Language> getLanguages(Context context) throws Exception {
        List<Language> result = cached;
        if (result != null) return result;
        synchronized (TextMateLanguageRegistry.class) {
            if (cached == null) {
                cached = parse(readAsset(context, LANGUAGES_ASSET_PATH));
            }
            return cached;
        }
    }

    public static Map<String, String> getExtensionMap(Context context) throws Exception {
        Map<String, String> map = new HashMap<>();
        for (Language lang : getLanguages(context)) {
            for (String ext : lang.extensions) {
                map.put(ext.toLowerCase(Locale.ROOT), lang.scopeName);
            }
        }
        return map;
    }

    private static String readAsset(Context context, String path) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (var is = context.getAssets().open(path);
             var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static List<Language> parse(String json) throws Exception {
        List<Language> languages = new ArrayList<>();
        var entries = new JSONObject(json).getJSONArray("languages");
        for (int i = 0; i < entries.length(); i++) {
            var entry = entries.getJSONObject(i);
            List<String> extensions = new ArrayList<>();
            if (entry.has("extensions")) {
                var exts = entry.getJSONArray("extensions");
                for (int j = 0; j < exts.length(); j++) {
                    extensions.add(exts.getString(j));
                }
            }
            languages.add(new Language(
                    entry.getString("name"),
                    entry.getString("scopeName"),
                    extensions
            ));
        }
        return languages;
    }
}
