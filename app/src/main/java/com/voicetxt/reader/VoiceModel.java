package com.voicetxt.reader;

import com.k2fsa.sherpa.onnx.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** A validated, app-owned speech package; all paths are relative to its private directory. */
final class VoiceModel {
    String id,name,type,model,tokens,lexicon="",dict="",data="",vocoder="",digest="",voices="";
    int speakers=1,sampleRate=0;
    final LinkedHashSet<String> languages=new LinkedHashSet<>();
    final ArrayList<String> rules=new ArrayList<>(),lexicons=new ArrayList<>(),speakerNames=new ArrayList<>();
    File directory;
    boolean supports(String code){for(String language:languages)if(VoiceLanguages.canonical(language).equals(VoiceLanguages.canonical(code)))return true;return false;}
    String speakerName(int speaker){return speaker>=0&&speaker<speakerNames.size()?speakerNames.get(speaker):"";}
    int defaultSpeaker(String language){String prefix=VoiceLanguages.canonical(language).equals("zh")?"z":VoiceLanguages.canonical(language).equals("en")?"a":"";if(!prefix.isEmpty())for(int i=0;i<speakerNames.size();i++)if(speakerNames.get(i).startsWith(prefix))return i;return 0;}
    String key(int speaker){return "pack:"+id+":"+speaker;}
    File file(String relative)throws IOException{File result=new File(directory,relative);String root=directory.getCanonicalPath();if(relative.isEmpty()||!result.getCanonicalPath().startsWith(root+File.separator))throw new IOException("invalid");return result;}
    String path(String relative)throws IOException{
        // sherpa's FST/lexicon settings use comma-separated paths. An archive path
        // containing ',' must not be reinterpreted as additional native paths.
        if(relative.indexOf(',')>=0)throw new IOException("invalid");
        return relative.isEmpty()?"":file(relative).getAbsolutePath();
    }
    JSONObject json()throws JSONException{JSONObject value=new JSONObject();value.put("version",1).put("id",id).put("name",name).put("type",type).put("model",model).put("tokens",tokens).put("lexicon",lexicon).put("dict",dict).put("data",data).put("vocoder",vocoder).put("digest",digest).put("voices",voices).put("lexicons",new JSONArray(lexicons)).put("speakerNames",new JSONArray(speakerNames)).put("speakers",speakers).put("sampleRate",sampleRate).put("languages",new JSONArray(languages)).put("rules",new JSONArray(rules));return value;}
    static VoiceModel read(File directory)throws Exception{JSONObject value=jsonFile(new File(directory,"installed.json"));VoiceModel pack=new VoiceModel();pack.directory=directory;pack.id=value.getString("id");pack.name=value.getString("name");pack.type=value.getString("type");pack.model=value.getString("model");pack.tokens=value.getString("tokens");pack.lexicon=value.optString("lexicon");pack.dict=value.optString("dict");pack.data=value.optString("data");pack.vocoder=value.optString("vocoder");pack.voices=value.optString("voices");for(String key:new String[]{"lexicons","speakerNames"}){JSONArray list=value.optJSONArray(key);if(list!=null)for(int i=0;i<list.length();i++)(key.equals("lexicons")?pack.lexicons:pack.speakerNames).add(list.getString(i));}pack.digest=value.getString("digest");pack.speakers=value.getInt("speakers");pack.sampleRate=value.getInt("sampleRate");JSONArray languages=value.getJSONArray("languages");for(int i=0;i<languages.length();i++)pack.languages.add(languages.getString(i));JSONArray rules=value.getJSONArray("rules");for(int i=0;i<rules.length();i++)pack.rules.add(rules.getString(i));if(!pack.id.equals(directory.getName())||!pack.file(pack.model).isFile()||!pack.file(pack.tokens).isFile())throw new IOException("invalid");return pack;}
    static JSONObject jsonFile(File file)throws Exception{if(file.length()>1024*1024)throw new IOException("invalid");return new JSONObject(new String(readBytes(file),StandardCharsets.UTF_8));}
    static byte[] readBytes(File file)throws IOException{try(InputStream input=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if(out.size()+n>1024*1024)throw new IOException("invalid");out.write(buffer,0,n);}return out.toByteArray();}}
    static void walk(File directory,List<File> files)throws IOException{File[] children=directory.listFiles();if(children==null)throw new IOException("storage");for(File child:children){if(child.isDirectory())walk(child,files);else files.add(child);if(files.size()>20000)throw new IOException("archive");}}
    static String relative(File root,File file)throws IOException{String base=root.getCanonicalPath()+File.separator,path=file.getCanonicalPath();if(!path.startsWith(base))throw new IOException("invalid");return path.substring(base.length()).replace(File.separatorChar,'/');}
    static File named(List<File> files,String name)throws IOException{File result=null;for(File file:files)if(file.getName().equals(name)){if(result!=null)throw new IOException("incomplete");result=file;}return result;}
    static File folder(List<File> files,String name){for(File file:files){File at=file.getParentFile();while(at!=null){if(at.getName().equals(name))return at;at=at.getParentFile();}}return null;}
    static String configured(File root,JSONObject manifest,String key,File fallback)throws IOException{String path=manifest.optString(key,"");if(!path.isEmpty()){File result=new File(root,path);relative(root,result);if(!result.exists())throw new IOException("incomplete");return relative(root,result);}return fallback==null?"":relative(root,fallback);}
    static VoiceModel inspect(File root,String title,String selectedLanguage)throws Exception{
        ArrayList<File> files=new ArrayList<>();walk(root,files);File manifestFile=named(files,"voice-package.json");JSONObject manifest=manifestFile==null?new JSONObject():jsonFile(manifestFile);File base=manifestFile==null?root:manifestFile.getParentFile();
        File model=null;String declared=manifest.optString("model","");
        if(!declared.isEmpty()){model=new File(base,declared);relative(root,model);}
        else{ArrayList<File> candidates=new ArrayList<>();for(File file:files)if(file.getName().endsWith(".onnx")&&!file.getName().matches("(?i).*(vocos|vocoder).*"))candidates.add(file);if(candidates.size()==2){File quantized=null;for(File candidate:candidates)if(candidate.getName().contains("int8"))quantized=candidate;if(quantized!=null){candidates.clear();candidates.add(quantized);}}if(candidates.size()!=1)throw new IOException("incomplete");model=candidates.get(0);}
        if(!model.isFile())throw new IOException("incomplete");Map<String,String> metadata;try{metadata=OnnxMetadata.read(model);}catch(IOException error){throw new IOException("invalid",error);}
        VoiceModel pack=new VoiceModel();pack.directory=root;pack.id=root.getName();pack.name=manifest.optString("name",title);if(pack.name.length()>100)pack.name=pack.name.substring(0,100);
        String kind=metadata.get("model_type");if("vits".equals(kind))pack.type="vits";else if("matcha-tts".equals(kind)||"matcha".equals(kind))pack.type="matcha";else if("kokoro".equals(kind))pack.type="kokoro";else throw new IOException("unsupported");
        pack.languages.addAll(languages(metadata.get("language")));if(pack.languages.isEmpty())throw new IOException("unknown_language");if(!selectedLanguage.isEmpty()&&!pack.supports(selectedLanguage))throw new IOException("language");
        // Piper's sidecar can identify a more precise locale than generic "Chinese" metadata.
        File sidecar=new File(model.getParentFile(),model.getName()+".json");
        if(sidecar.isFile()){
            JSONObject settings=jsonFile(sidecar),locale=settings.optJSONObject("language");
            if(locale!=null){Set<String> precise=languages(locale.optString("code",""));if(!precise.isEmpty()){if(!pack.languages.containsAll(precise))throw new IOException("language");pack.languages.retainAll(precise);}}
            if(!selectedLanguage.isEmpty()&&!pack.supports(selectedLanguage))throw new IOException("language");
        }
        // A manifest may narrow the model's language list, but cannot claim an unrelated language.
        if(manifest.has("languages")){LinkedHashSet<String> declaredLanguages=new LinkedHashSet<>();JSONArray list=manifest.getJSONArray("languages");for(int i=0;i<list.length();i++)declaredLanguages.addAll(languages(list.getString(i)));if(declaredLanguages.isEmpty()||!pack.languages.containsAll(declaredLanguages))throw new IOException("language");pack.languages.retainAll(declaredLanguages);if(!selectedLanguage.isEmpty()&&!pack.supports(selectedLanguage))throw new IOException("language");}
        pack.model=relative(root,model);pack.tokens=configured(base,manifest,"tokens",named(files,"tokens.txt"));pack.lexicon=configured(base,manifest,"lexicon",named(files,"lexicon.txt"));pack.data=configured(base,manifest,"data",folder(files,"espeak-ng-data"));pack.dict=configured(base,manifest,"dict",folder(files,"dict"));
        if(pack.type.equals("kokoro")){pack.voices=configured(base,manifest,"voices",named(files,"voices.bin"));if(!base.equals(root))pack.voices=rebase(root,base,pack.voices);if(pack.voices.isEmpty()||pack.file(pack.voices).length()==0||pack.data.isEmpty())throw new IOException("incomplete");for(String required:new String[]{"phontab","phonindex","phondata","intonations"})if(!new File(new File(base,pack.data),required).isFile())throw new IOException("incomplete");for(String filename:new String[]{"lexicon-us-en.txt","lexicon-zh.txt"}){File found=named(files,filename);if(found!=null)pack.lexicons.add(relative(root,found));}if(pack.lexicons.isEmpty()&&!pack.lexicon.isEmpty())pack.lexicons.add(base.equals(root)?pack.lexicon:rebase(root,base,pack.lexicon));if(pack.languages.contains("zh")&&named(files,"lexicon-zh.txt")==null&&pack.lexicon.isEmpty())throw new IOException("incomplete");String names=metadata.getOrDefault("speaker_names","");if(!names.isEmpty())pack.speakerNames.addAll(Arrays.asList(names.split(",")));}
        // configured() returns paths relative to a manifest's directory; rebase them to the package root.
        if(!base.equals(root)){pack.tokens=rebase(root,base,pack.tokens);pack.lexicon=rebase(root,base,pack.lexicon);pack.data=rebase(root,base,pack.data);pack.dict=rebase(root,base,pack.dict);}
        if(pack.tokens.isEmpty()||pack.file(pack.tokens).length()==0)throw new IOException("incomplete");
        // eSpeak-backed Chinese Piper voices use their data directory instead of a lexicon.
        boolean espeak="1".equals(metadata.get("has_espeak"));
        if((pack.languages.contains("zh")||pack.languages.contains("zh-Hant"))&&!pack.type.equals("kokoro")&&!espeak&&(pack.lexicon.isEmpty()||pack.file(pack.lexicon).length()==0))throw new IOException("incomplete");
        if("1".equals(metadata.get("has_espeak"))&&pack.data.isEmpty())throw new IOException("incomplete");
        if("1".equals(metadata.get("jieba"))&&pack.dict.isEmpty())throw new IOException("incomplete");
        if(pack.type.equals("matcha")){File vocoder=named(files,"vocos.onnx");if(vocoder==null)vocoder=named(files,"vocoder.onnx");String configured=configured(base,manifest,"vocoder",vocoder);pack.vocoder=base.equals(root)?configured:rebase(root,base,configured);if(pack.vocoder.isEmpty())throw new IOException("incomplete");}
        for(String rule:new String[]{"phone.fst","date.fst","number.fst","new_heteronym.fst","phone-zh.fst","date-zh.fst","number-zh.fst"}){File file=named(files,rule);if(file!=null)pack.rules.add(relative(root,file));}
        try{pack.speakers=Integer.parseInt(metadata.getOrDefault("n_speakers","1"));pack.sampleRate=Integer.parseInt(metadata.getOrDefault("sample_rate","0"));}catch(NumberFormatException error){throw new IOException("invalid");}
        if(pack.speakers<0||pack.speakers>10000||pack.sampleRate<8000||pack.sampleRate>96000)throw new IOException("invalid");pack.speakers=Math.max(1,pack.speakers);if(!pack.speakerNames.isEmpty()&&pack.speakerNames.size()!=pack.speakers)throw new IOException("invalid");return pack;
    }
    static String rebase(File root,File base,String path)throws IOException{return path.isEmpty()?"":relative(root,new File(base,path));}
    static Set<String> languages(String text){return VoiceLanguages.parse(text);}

    OfflineTts createEngine()throws IOException{
        OfflineTtsModelConfig models=new OfflineTtsModelConfig();models.setNumThreads(2);
        if(type.equals("vits")){OfflineTtsVitsModelConfig config=new OfflineTtsVitsModelConfig();config.setModel(path(model));config.setTokens(path(tokens));config.setLexicon(path(lexicon));config.setDataDir(path(data));config.setDictDir(path(dict));models.setVits(config);}
        else if(type.equals("matcha")){OfflineTtsMatchaModelConfig config=new OfflineTtsMatchaModelConfig();config.setAcousticModel(path(model));config.setVocoder(path(vocoder));config.setTokens(path(tokens));config.setLexicon(path(lexicon));config.setDataDir(path(data));config.setDictDir(path(dict));models.setMatcha(config);}
        else if(type.equals("kokoro")){OfflineTtsKokoroModelConfig config=new OfflineTtsKokoroModelConfig();config.setModel(path(model));config.setVoices(path(voices));config.setTokens(path(tokens));config.setDataDir(path(data));ArrayList<String> paths=new ArrayList<>();for(String relative:lexicons)paths.add(path(relative));config.setLexicon(android.text.TextUtils.join(",",paths));config.setLang("en-us");models.setKokoro(config);}
        else throw new IOException("unsupported");
        OfflineTtsConfig config=new OfflineTtsConfig();config.setModel(models);config.setMaxNumSentences(1);ArrayList<String> paths=new ArrayList<>();for(String rule:rules)paths.add(path(rule));config.setRuleFsts(android.text.TextUtils.join(",",paths));return new OfflineTts(null,config);
    }
}
