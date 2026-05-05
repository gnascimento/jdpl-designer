package br.com.caffeineti.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

public final class Messages {
    public static final Locale ENGLISH = Locale.ENGLISH;
    public static final Locale PORTUGUESE_BRAZIL = Locale.forLanguageTag("pt-BR");

    private static final String BUNDLE_NAME = "br.com.caffeineti.i18n.messages";
    private static final String LOCALE_PROPERTY = "jdpl.locale";
    private static final String PREF_LOCALE = "locale";
    private static final Preferences PREFS = Preferences.userNodeForPackage(Messages.class);

    private static Locale locale = loadInitialLocale();
    private static ResourceBundle bundle = loadBundle(locale);

    private Messages() {}

    public static Locale getLocale() {
        return locale;
    }

    public static void setLocale(Locale newLocale) {
        locale = supportedLocale(newLocale);
        Locale.setDefault(locale);
        bundle = loadBundle(locale);
        PREFS.put(PREF_LOCALE, locale.toLanguageTag());
    }

    public static String get(String key, Object... args) {
        String pattern;
        try {
            pattern = bundle.getString(key);
        } catch (MissingResourceException ex) {
            pattern = key;
        }
        return args.length == 0 ? pattern : MessageFormat.format(pattern, args);
    }

    private static Locale loadInitialLocale() {
        String configured = System.getProperty(LOCALE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = PREFS.get(PREF_LOCALE, ENGLISH.toLanguageTag());
        }
        Locale loaded = Locale.forLanguageTag(configured);
        Locale supported = supportedLocale(loaded);
        Locale.setDefault(supported);
        return supported;
    }

    private static Locale supportedLocale(Locale candidate) {
        if (candidate != null
                && "pt".equalsIgnoreCase(candidate.getLanguage())
                && "BR".equalsIgnoreCase(candidate.getCountry())) {
            return PORTUGUESE_BRAZIL;
        }
        return ENGLISH;
    }

    private static ResourceBundle loadBundle(Locale bundleLocale) {
        return ResourceBundle.getBundle(BUNDLE_NAME, bundleLocale);
    }
}
