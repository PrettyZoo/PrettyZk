package cc.cc1234.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class JsonUtils {

    public static <T> T from(String jsonFile, Class<T> clazz) {
        final ObjectMapper mapper = mapper();
        try {
            final Path path = Paths.get(jsonFile);
            path.getParent().toFile().mkdirs();
            if (!Files.exists(path)) {
                Files.createFile(path);
                Files.write(path, "{}".getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE);
            }
            return mapper.readValue(path.toFile(), clazz);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        final ObjectMapper mapper = mapper();
        try {
            return mapper.readValue(json.getBytes(StandardCharsets.UTF_8), clazz);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> String to(T t) {
        final ObjectMapper mapper = mapper();
        try {
            return mapper.writeValueAsString(t);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ObjectMapper mapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }
}
