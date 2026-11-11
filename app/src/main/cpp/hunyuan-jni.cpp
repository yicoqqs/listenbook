#include <jni.h>
#include <llama.h>
#include <atomic>
#include <algorithm>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>
#include <memory>
#include <sys/auxv.h>
#include <asm/hwcap.h>

namespace {
struct Engine {
    llama_model *model=nullptr; llama_context *context=nullptr;
    std::atomic<int64_t> floor{0},running{0};
    ~Engine(){if(context)llama_free(context);if(model)llama_model_free(model);}
};
std::once_flag initialized;
bool aborted(void *data){auto *engine=static_cast<Engine*>(data);return engine->running.load()<engine->floor.load();}
thread_local std::string diagnostic;
void quiet(enum ggml_log_level level,const char *message,void *){if(level==GGML_LOG_LEVEL_ERROR&&diagnostic.size()<4096)diagnostic.append(message);}
std::string utf8(JNIEnv *env,jstring value){
    jclass cls=env->FindClass("java/lang/String");jmethodID method=env->GetMethodID(cls,"getBytes","(Ljava/lang/String;)[B");jstring charset=env->NewStringUTF("UTF-8");
    auto bytes=static_cast<jbyteArray>(env->CallObjectMethod(value,method,charset));std::string result(env->GetArrayLength(bytes),'\0');env->GetByteArrayRegion(bytes,0,result.size(),reinterpret_cast<jbyte*>(result.data()));env->DeleteLocalRef(bytes);env->DeleteLocalRef(charset);env->DeleteLocalRef(cls);return result;
}
jstring java_string(JNIEnv *env,const std::string &value){jbyteArray bytes=env->NewByteArray(value.size());env->SetByteArrayRegion(bytes,0,value.size(),reinterpret_cast<const jbyte*>(value.data()));jclass cls=env->FindClass("java/lang/String");jmethodID constructor=env->GetMethodID(cls,"<init>","([BLjava/lang/String;)V");jstring charset=env->NewStringUTF("UTF-8");auto result=static_cast<jstring>(env->NewObject(cls,constructor,bytes,charset));env->DeleteLocalRef(bytes);env->DeleteLocalRef(charset);env->DeleteLocalRef(cls);return result;}
void fail(JNIEnv *env,const std::exception &error){env->ThrowNew(env->FindClass("java/io/IOException"),error.what());}
}
extern "C" JNIEXPORT jlong JNICALL Java_com_voicetxt_reader_HunyuanBridge_load(JNIEnv *env,jclass,jstring path,jint threads){
    try{
        unsigned long caps=getauxval(AT_HWCAP);
        if(!(caps&HWCAP_ASIMDDP)||!(caps&HWCAP_ASIMDHP))throw std::runtime_error("unsupported_cpu");
        std::call_once(initialized,[]{llama_log_set(quiet,nullptr);llama_backend_init();});diagnostic.clear();
        auto engine=std::make_unique<Engine>();auto model=llama_model_default_params();model.n_gpu_layers=0;model.load_mode=LLAMA_LOAD_MODE_MMAP;
        engine->model=llama_model_load_from_file(utf8(env,path).c_str(),model);if(!engine->model)throw std::runtime_error("model_load: "+diagnostic);
        auto context=llama_context_default_params();context.n_ctx=1536;context.n_batch=256;context.n_ubatch=128;context.n_threads=std::max(1,std::min(4,static_cast<int>(threads)));context.n_threads_batch=context.n_threads;context.no_perf=true;
        engine->context=llama_init_from_model(engine->model,context);if(!engine->context)throw std::runtime_error("context_load");llama_set_abort_callback(engine->context,aborted,engine.get());return reinterpret_cast<jlong>(engine.release());
    }catch(const std::exception &error){fail(env,error);return 0;}
}
extern "C" JNIEXPORT jstring JNICALL Java_com_voicetxt_reader_HunyuanBridge_generate(JNIEnv *env,jclass,jlong pointer,jstring input,jint limit,jlong epoch){
    try{
        auto *engine=reinterpret_cast<Engine*>(pointer);if(!engine)throw std::runtime_error("closed");engine->running=epoch;if(aborted(engine))throw std::runtime_error("cancelled");
        std::string prompt="<｜hy_begin▁of▁sentence｜><｜hy_User｜>"+utf8(env,input)+"<｜hy_Assistant｜>";
        const auto *vocabulary=llama_model_get_vocab(engine->model);int count=-llama_tokenize(vocabulary,prompt.data(),prompt.size(),nullptr,0,false,true);if(count<=0||count>1050)throw std::runtime_error("input_length");std::vector<llama_token> tokens(count);if(llama_tokenize(vocabulary,prompt.data(),prompt.size(),tokens.data(),tokens.size(),false,true)<0)throw std::runtime_error("tokenize");
        llama_memory_clear(llama_get_memory(engine->context),true);
        for(int start=0;start<count;start+=256){if(aborted(engine))throw std::runtime_error("cancelled");auto batch=llama_batch_get_one(tokens.data()+start,std::min(256,count-start));if(llama_decode(engine->context,batch)!=0)throw std::runtime_error(aborted(engine)?"cancelled":"prefill");}
        auto deleter=[](llama_sampler *sampler){llama_sampler_free(sampler);};std::unique_ptr<llama_sampler,decltype(deleter)> sampler(llama_sampler_chain_init(llama_sampler_chain_default_params()),deleter);
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_penalties(llama_vocab_n_tokens(vocabulary),64,1.05f,0,0));llama_sampler_chain_add(sampler.get(),llama_sampler_init_greedy());
        std::string output;std::vector<char> piece(512);bool ended=false;
        for(int n=0;n<std::min(static_cast<int>(limit),384);n++){
            if(aborted(engine))throw std::runtime_error("cancelled");llama_token token=llama_sampler_sample(sampler.get(),engine->context,-1);if(llama_vocab_is_eog(vocabulary,token)){ended=true;break;}
            int bytes=llama_token_to_piece(vocabulary,token,piece.data(),piece.size(),0,false);if(bytes<0){piece.resize(-bytes);bytes=llama_token_to_piece(vocabulary,token,piece.data(),piece.size(),0,false);}if(bytes>0)output.append(piece.data(),bytes);
            auto batch=llama_batch_get_one(&token,1);if(llama_decode(engine->context,batch)!=0)throw std::runtime_error(aborted(engine)?"cancelled":"decode");
        }
        if(!ended)throw std::runtime_error("output_length");return java_string(env,output);
    }catch(const std::exception &error){fail(env,error);return nullptr;}
}
extern "C" JNIEXPORT void JNICALL Java_com_voicetxt_reader_HunyuanBridge_cancel(JNIEnv *,jclass,jlong pointer,jlong epoch){if(pointer)reinterpret_cast<Engine*>(pointer)->floor=epoch;}
extern "C" JNIEXPORT void JNICALL Java_com_voicetxt_reader_HunyuanBridge_release(JNIEnv *,jclass,jlong pointer){delete reinterpret_cast<Engine*>(pointer);}
