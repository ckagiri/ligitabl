package com.ligitabl.seed.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SeedLoader {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    @SuppressWarnings("unchecked")
    public Map<String, Object> loadFromClasspath(String mainResource) throws IOException {
        try (InputStream in = resource(mainResource)) {
            if (in == null) {
                throw new IllegalArgumentException("Seed main resource not found: " + mainResource);
            }
            Map<String, Object> root = mapper.readValue(in, Map.class);
            Object seedObj = root.get("seed");
            if (!(seedObj instanceof List<?> includes)) {
                return Map.of();
            }
            Map<String, Object> merged = new HashMap<>();
            String base = basePath(mainResource);
            for (Object entry : includes) {
                if (!(entry instanceof Map<?, ?> mapEntry)) {
                    continue;
                }
                Object filePath = mapEntry.get("file");
                if (filePath == null) {
                    continue;
                }
                String childResource = base + filePath;
                try (InputStream childIn = resource(childResource)) {
                    if (childIn == null) {
                        if (isOptionalInclude(mapEntry, String.valueOf(filePath))) {
                            continue;
                        }
                        throw new IllegalArgumentException(
                                "Seed include resource not found: " + childResource);
                    }
                    Map<String, Object> childMap = mapper.readValue(childIn, Map.class);
                    merged.putAll(childMap);
                }
            }
            return merged;
        }
    }

    private boolean isOptionalInclude(Map<?, ?> mapEntry, String fileName) {
        Object optional = mapEntry.get("optional");
        if (optional instanceof Boolean b && b) {
            return true;
        }
        return "demo-team.yaml".equals(fileName);
    }

    private InputStream resource(String path) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
    }

    private String basePath(String resource) {
        int idx = resource.lastIndexOf('/') + 1;
        return idx <= 0 ? "" : resource.substring(0, idx);
    }
}

