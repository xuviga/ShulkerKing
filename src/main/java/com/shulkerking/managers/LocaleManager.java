package com.shulkerking.managers;

import com.shulkerking.ShulkerKingPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class LocaleManager {
    
    private final ShulkerKingPlugin plugin;
    private final Map<String, FileConfiguration> languages;
    private final String defaultLanguage;
    
    public LocaleManager(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
        this.languages = new HashMap<>();
        this.defaultLanguage = plugin.getConfig().getString("language.default", "en_us");
        
        loadLanguages();
    }
    
    private void loadLanguages() {
        // Supported languages
        String[] supportedLanguages = {
            "en_us", "ru_ru", "es_es", "de_de", "fr_fr", 
            "zh_cn", "ja_jp", "pt_br", "it_it", "pl_pl"
        };
        
        for (String lang : supportedLanguages) {
            loadLanguage(lang);
        }
        
        plugin.getLogger().info("Loaded " + languages.size() + " language files");
    }
    
    private void loadLanguage(String languageCode) {
        try {
            // Try to load from plugin data folder first
            File langFile = new File(plugin.getDataFolder(), "languages/" + languageCode + ".yml");
            FileConfiguration config;
            
            if (langFile.exists()) {
                config = YamlConfiguration.loadConfiguration(langFile);
            } else {
                // Load from resources
                InputStream stream = plugin.getResource("languages/" + languageCode + ".yml");
                if (stream == null) {
                    plugin.getLogger().warning("Language file not found: " + languageCode + ".yml");
                    return;
                }
                
                config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
                
                // Save to data folder for customization
                saveLanguageFile(languageCode, config);
            }
            
            languages.put(languageCode, config);
            plugin.debugLog("Loaded language: " + languageCode);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load language: " + languageCode, e);
        }
    }
    
    private void saveLanguageFile(String languageCode, FileConfiguration config) {
        try {
            File langDir = new File(plugin.getDataFolder(), "languages");
            if (!langDir.exists()) {
                langDir.mkdirs();
            }
            
            File langFile = new File(langDir, languageCode + ".yml");
            config.save(langFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save language file: " + languageCode, e);
        }
    }
    
    public String getMessage(Player player, String key) {
        String locale = getPlayerLocale(player);
        return getMessage(locale, key);
    }
    
    public String getMessage(String locale, String key) {
        // Check if auto-detect is disabled
        boolean autoDetect = plugin.getConfig().getBoolean("language.auto-detect", true);
        if (!autoDetect) {
            locale = defaultLanguage;
        }
        
        // Try player's locale first
        FileConfiguration langConfig = languages.get(locale.toLowerCase());
        
        if (langConfig != null && langConfig.contains(key)) {
            return colorize(langConfig.getString(key));
        }
        
        // Fallback to default language
        langConfig = languages.get(defaultLanguage);
        if (langConfig != null && langConfig.contains(key)) {
            return colorize(langConfig.getString(key));
        }
        
        // Final fallback to English
        langConfig = languages.get("en_us");
        if (langConfig != null && langConfig.contains(key)) {
            return colorize(langConfig.getString(key));
        }
        
        // Return key if nothing found
        return "&cMessage not found: " + key;
    }
    
    public String getPlayerLocale(Player player) {
        try {
            // Try to get client locale using reflection (works on Paper/Spigot 1.12+)
            String locale = player.getLocale();
            if (locale != null && !locale.isEmpty()) {
                String normalizedLocale = locale.toLowerCase().replace("-", "_");
                plugin.debugLog("Player " + player.getName() + " locale: " + locale + " -> " + normalizedLocale);
                
                // Map common Russian locales
                if (normalizedLocale.startsWith("ru")) {
                    return "ru_ru";
                }
                
                // Check if we have this exact locale
                if (languages.containsKey(normalizedLocale)) {
                    return normalizedLocale;
                }
                
                // Try language code only (e.g., "en" from "en_us")
                String langCode = normalizedLocale.split("_")[0];
                for (String supportedLang : languages.keySet()) {
                    if (supportedLang.startsWith(langCode + "_")) {
                        return supportedLang;
                    }
                }
                
                return normalizedLocale;
            }
        } catch (Exception e) {
            plugin.debugLog("Failed to get player locale for " + player.getName() + ": " + e.getMessage());
        }
        
        // Fallback to default language
        return defaultLanguage;
    }
    
    private String colorize(String message) {
        if (message == null) return "";
        return message.replace('&', '§');
    }
    
    public void reloadLanguages() {
        languages.clear();
        loadLanguages();
    }
    
    public boolean isLanguageSupported(String locale) {
        return languages.containsKey(locale.toLowerCase());
    }
    
    public String[] getSupportedLanguages() {
        return languages.keySet().toArray(new String[0]);
    }
}
