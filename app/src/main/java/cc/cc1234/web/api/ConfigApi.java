package cc.cc1234.web.api;

import cc.cc1234.core.configuration.entity.Configuration;
import cc.cc1234.core.configuration.service.ConfigurationDomainService;
import cc.cc1234.version.Version;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

public class ConfigApi {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigApi.class);

    private final ConfigurationDomainService configurationDomainService;

    public ConfigApi(ConfigurationDomainService configurationDomainService) {
        this.configurationDomainService = configurationDomainService;
    }

    public void getVersion(Context ctx) {
        ctx.json(Map.of(
                "version", Version.VERSION,
                "name", "PrettyZk"
        ));
    }

    public void getConfig(Context ctx) {
        ensureConfigLoaded();
        var config = configurationDomainService.get().orElse(null);
        if (config == null) {
            ctx.json(Map.of("theme", "default", "fontSize", 14, "locale", Locale.getDefault().toLanguageTag()));
            return;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("theme", config.getTheme() != null ? config.getTheme() : "default");
        result.put("fontSize", config.getFontConfiguration() != null ?
                config.getFontConfiguration().getSize() : 14);
        result.put("locale", config.getLocaleConfiguration() != null ?
                config.getLocaleConfiguration().getLocale().toLanguageTag() :
                Locale.getDefault().toLanguageTag());
        ctx.json(result);
    }

    public void updateTheme(Context ctx) {
        ensureConfigLoaded();
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String theme = body.get("theme");
            if (theme == null || (!theme.equals("default") && !theme.equals("dark"))) {
                ctx.status(400);
                ctx.json(Map.of("error", "theme must be 'default' or 'dark'"));
                return;
            }
            configurationDomainService.saveTheme(theme);
            ctx.json(Map.of("theme", theme));
        } catch (Exception e) {
            ctx.status(400);
            ctx.json(Map.of("error", "Invalid request"));
        }
    }

    public void updateFontSize(Context ctx) {
        ensureConfigLoaded();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Integer fontSize = body.get("fontSize") instanceof Number n ?
                    n.intValue() : null;
            if (fontSize == null || fontSize < 8 || fontSize > 25) {
                ctx.status(400);
                ctx.json(Map.of("error", "fontSize must be between 8 and 25"));
                return;
            }
            configurationDomainService.save(new Configuration.FontConfiguration(fontSize));
            ctx.json(Map.of("fontSize", fontSize));
        } catch (Exception e) {
            ctx.status(400);
            ctx.json(Map.of("error", "Invalid request"));
        }
    }

    public void updateLocale(Context ctx) {
        ensureConfigLoaded();
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String langTag = body.get("locale");
            if (langTag == null) {
                ctx.status(400);
                ctx.json(Map.of("error", "locale is required"));
                return;
            }
            configurationDomainService.save(new Configuration.LocaleConfiguration(Locale.forLanguageTag(langTag)));
            ctx.json(Map.of("locale", langTag));
        } catch (Exception e) {
            ctx.status(400);
            ctx.json(Map.of("error", "Invalid locale"));
        }
    }

    public void exportConfig(Context ctx) {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("prettyZoo-config-", ".json");
            configurationDomainService.exportConfig(tempFile);
            String content = Files.readString(tempFile.toPath());
            ctx.contentType("application/json");
            ctx.result(content);
        } catch (Exception e) {
            LOG.error("Failed to export config", e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to export config"));
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile.toPath()); } catch (Exception ignored) {}
            }
        }
    }

    public void importConfig(Context ctx) {
        File tempFile = null;
        try {
            String bodyStr = ctx.body();
            tempFile = File.createTempFile("prettyZoo-config-import-", ".json");
            Files.writeString(tempFile.toPath(), bodyStr);
            configurationDomainService.importConfig(tempFile);
            ctx.json(Map.of("status", "imported"));
        } catch (Exception e) {
            LOG.error("Failed to import config", e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to import config"));
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile.toPath()); } catch (Exception ignored) {}
            }
        }
    }

    private void ensureConfigLoaded() {
        if (configurationDomainService.get().isEmpty()) {
            configurationDomainService.load(List.of());
        }
    }
}
