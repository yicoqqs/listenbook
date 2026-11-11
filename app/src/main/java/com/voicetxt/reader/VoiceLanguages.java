package com.voicetxt.reader;

import java.util.*;

/** Canonical voice languages shared by reading targets and installed voice management. */
final class VoiceLanguages {
    static final String[] CODES={"zh","en","ja","ko","fr","de","es","pt","ru","ar","hi","id","vi","th"};
    static String canonical(String code){if(code==null)return "";String normalized=code.replace('_','-').toLowerCase(Locale.ROOT);int dash=normalized.indexOf('-');String base=dash<0?normalized:normalized.substring(0,dash);return base.equals("in")?"id":base.equals("fil")?"tl":base.equals("iw")?"he":base.equals("ji")?"yi":base.equals("cmn")?"zh":base;}
    static final Map<String,String> LANGUAGE_NAMES=languageNames();
    static Map<String,String> languageNames(){Map<String,String> names=new HashMap<>();for(String code:Locale.getISOLanguages()){Locale locale=Locale.forLanguageTag(code);String canonical=canonical(code);names.put(code,canonical);names.put(canonical,canonical);names.put(locale.getDisplayLanguage(Locale.ENGLISH).toLowerCase(Locale.ROOT),canonical);try{names.put(locale.getISO3Language(),canonical);}catch(MissingResourceException ignored){}}for(Locale locale:Locale.getAvailableLocales()){String code=canonical(locale.getLanguage());if(code.isEmpty()||code.equals("und"))continue;names.put(code,code);names.put(locale.getLanguage(),code);names.put(locale.getDisplayLanguage(Locale.ENGLISH).toLowerCase(Locale.ROOT),code);}for(String code:new String[]{"yue","fil","nan","cmn"})names.put(code,canonical(code));names.put("mandarin","zh");names.put("cantonese","yue");names.put("filipino","tl");names.put("farsi","fa");names.put("burmese","my");return names;}
    static boolean known(String code){return !code.isEmpty()&&!code.equals("und")&&LANGUAGE_NAMES.containsValue(canonical(code));}
    static Set<String> parse(String value){Set<String> result=new LinkedHashSet<>();if(value==null)return result;String normalized=value.toLowerCase(Locale.ROOT).replace('_','-').trim();String full=LANGUAGE_NAMES.get(normalized);if(full!=null&&known(full)){result.add(full);return result;}for(String token:normalized.split("[^a-z0-9-]+")){String language=LANGUAGE_NAMES.get(token);if(language==null&&token.contains("-"))language=LANGUAGE_NAMES.get(canonical(token));if(language!=null&&known(language))result.add(language);}return result;}
    static List<String> targets(SystemSpeaker system,VoicePackages packages){List<String> values=available(system,packages);values.removeIf(code->!TranslationText.SUPPORTED.contains(code));return values;}
    static String base(String code){return canonical(code);}
    static boolean supported(Locale locale){return known(canonical(locale.getLanguage()));}
    static boolean matches(String code,Locale locale){return canonical(code).equals(canonical(locale.getLanguage()));}
    static String name(String code,Locale display){return Locale.forLanguageTag(canonical(code)).getDisplayLanguage(display);}
    static String systemLanguage(android.content.Context context){return canonical(android.content.res.Resources.getSystem().getConfiguration().getLocales().get(0).getLanguage());}
    static String preference(String code){code=canonical(code);if(code.isEmpty()||code.equals("zh"))return "chineseVoiceKey";if(code.equals("en"))return "englishVoiceKey";if(code.equals("ja"))return "japaneseVoiceKey";return "voice_"+code;}
    static List<String> available(SystemSpeaker system,VoicePackages packages){LinkedHashSet<String> installed=new LinkedHashSet<>();for(VoiceModel pack:packages.all())for(String code:pack.languages)if(known(code))installed.add(canonical(code));if(system!=null&&system.initialized)for(SystemSpeaker.Option option:system.options)if(SystemSpeaker.offlineUsable(option.voice)&&supported(option.voice.getLocale()))installed.add(canonical(option.voice.getLocale().getLanguage()));List<String> result=new ArrayList<>();for(String code:CODES)if(installed.remove(code))result.add(code);List<String> extra=new ArrayList<>(installed);Collections.sort(extra);result.addAll(extra);return result;}
    // Preserve exact voice/speaker choices when the former Chinese script categories merge.
    static void migrate(android.content.SharedPreferences prefs){if(prefs.getBoolean("merged_chinese_voice_v1",false))return;String target=prefs.getString("readingLanguage","");Locale device=android.content.res.Resources.getSystem().getConfiguration().getLocales().get(0);boolean active=target.equals("zh-Hant")||(target.isEmpty()&&device.getLanguage().equals("zh")&&(device.getScript().equals("Hant")||Arrays.asList("TW","HK","MO").contains(device.getCountry())));String legacy=prefs.getString("voice_zh-Hant",active?prefs.getString("systemVoiceKey",""):"");android.content.SharedPreferences.Editor edit=prefs.edit();if(!legacy.isEmpty()&&(active||!prefs.contains("chineseVoiceKey"))){edit.putString("chineseVoiceKey",legacy);if(active)edit.putString("systemVoiceKey",legacy);}if(!target.isEmpty())edit.putString("readingLanguage",canonical(target));for(Map.Entry<String,?> entry:prefs.getAll().entrySet())if(entry.getKey().startsWith("pack_speaker_")&&entry.getKey().endsWith("_zh-Hant")&&entry.getValue() instanceof Integer){String key=entry.getKey().substring(0,entry.getKey().length()-7)+"zh";if(active||!prefs.contains(key))edit.putInt(key,(Integer)entry.getValue());}edit.putBoolean("merged_chinese_voice_v1",true).apply();}
    static String sample(String code){switch(canonical(code)){
        case "zh":return "你好，欢迎使用我爱听书。现在听到的是你试听的声音。";
        case "zh-Hant":return "你好，歡迎使用我爱听书。現在聽到的是你試聽的聲音。";
        case "en":return "Hello, welcome to Audiobook Reader. This is a preview of the selected voice.";
        case "ja":return "こんにちは。有声読書へようこそ。これは選択した音声のサンプルです。";
        case "ko":return "안녕하세요. 오디오북 리더에 오신 것을 환영합니다. 선택한 음성을 미리 듣고 있습니다.";
        case "fr":return "Bonjour, bienvenue dans notre application de lecture. Vous écoutez un aperçu de la voix sélectionnée.";
        case "de":return "Hallo und willkommen in unserer Lese-App. Sie hören eine Vorschau der ausgewählten Stimme.";
        case "es":return "Hola, te damos la bienvenida a nuestra aplicación de lectura. Esta es una muestra de la voz seleccionada.";
        case "pt":return "Olá, bem-vindo ao nosso aplicativo de leitura. Esta é uma amostra da voz selecionada.";
        case "ru":return "Здравствуйте! Добро пожаловать в приложение для чтения. Сейчас вы слышите образец выбранного голоса.";
        case "ar":return "مرحبًا بك في تطبيق القراءة. أنت تستمع الآن إلى عينة من الصوت الذي اخترته.";
        case "hi":return "नमस्ते, हमारी पुस्तक पढ़ने की ऐप में आपका स्वागत है। आप चुनी हुई आवाज़ का नमूना सुन रहे हैं।";
        case "id":return "Halo, selamat datang di aplikasi pembaca buku. Anda sedang mendengarkan contoh suara yang dipilih.";
        case "vi":return "Xin chào, chào mừng bạn đến với ứng dụng đọc sách. Đây là bản nghe thử giọng đọc bạn đã chọn.";
        case "th":return "สวัสดี ยินดีต้อนรับสู่แอปอ่านหนังสือ นี่คือตัวอย่างเสียงที่คุณเลือก";
        case "it":return "Ciao, benvenuto nella nostra app di lettura. Questa è la voce selezionata.";
        case "nl":return "Hallo, welkom bij onze leesapp. Dit is een voorbeeld van de gekozen stem.";
        case "pl":return "Witaj w naszej aplikacji. To jest próbka wybranego głosu.";
        case "uk":return "Вітаємо у нашому застосунку для читання. Це зразок вибраного голосу.";
        case "tr":return "Merhaba, kitap okuma uygulamamıza hoş geldiniz. Bu seçtiğiniz sesin bir örneğidir.";
        case "fa":return "سلام، به برنامه کتابخوان خوش آمدید. این نمونه صدای انتخابی شما است.";
        case "sv":return "Hej och välkommen. Det här är ett exempel på den valda rösten.";
        case "cs":return "Dobrý den, vítejte v naší čtecí aplikaci. Toto je ukázka vybraného hlasu.";
        case "fi":return "Hei ja tervetuloa. Tämä on esimerkki valitusta äänestä.";
        case "el":return "Γεια σας, καλώς ήρθατε. Αυτό είναι ένα δείγμα της επιλεγμένης φωνής.";
        case "ro":return "Bună ziua, bine ați venit. Aceasta este vocea selectată.";
        case "hu":return "Üdvözöljük az alkalmazásban. Ez a kiválasztott hang mintája.";
        case "da":return "Hej og velkommen. Dette er en prøve på den valgte stemme.";
        case "no":case "nb":return "Hei og velkommen. Dette er en prøve på den valgte stemmen.";
        case "he":return "שלום, ברוכים הבאים. זוהי דוגמה של הקול שבחרתם.";
        default:return "1, 2, 3, 4, 5.";
    }}
}
