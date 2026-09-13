// Puente JNI minimo entre Kotlin (Llm.kt) y llama.cpp. Solo la API C de
// llama.h: cargar un modelo GGUF, armar el prompt con la plantilla de chat que
// trae el modelo, y generar tokens de a uno para que Kotlin los muestre en
// streaming. Un solo modelo cargado a la vez; todo se llama desde un unico
// hilo de fondo (Llm.kt lo garantiza).

#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <string>
#include <vector>

#include "llama.h"

#define TAG "HabloLlm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct Engine {
    llama_model   * model   = nullptr;
    llama_context * ctx     = nullptr;
    llama_sampler * sampler = nullptr;
    const llama_vocab * vocab = nullptr;
    int n_ctx = 0;
    int n_past = 0;                  // tokens ya en la memoria del contexto
    int answer_start = 0;            // donde empezo la respuesta en curso
    bool generating = false;
    std::string pending;             // bytes UTF-8 incompletos entre tokens
};

Engine * g = nullptr;

void log_cb(ggml_log_level level, const char * text, void *) {
    // llama.cpp es muy hablador: solo errores y avisos al logcat
    if (level == GGML_LOG_LEVEL_ERROR) __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", text);
    else if (level == GGML_LOG_LEVEL_WARN) __android_log_print(ANDROID_LOG_WARN, TAG, "%s", text);
}

std::string jstr(JNIEnv * env, jstring s) {
    if (!s) return "";
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

std::vector<llama_token> tokenize(const llama_vocab * vocab, const std::string & text, bool add_special) {
    int n = -llama_tokenize(vocab, text.c_str(), (int) text.size(), nullptr, 0, add_special, true);
    std::vector<llama_token> out(n > 0 ? n : 0);
    if (n > 0) {
        llama_tokenize(vocab, text.c_str(), (int) text.size(), out.data(), n, add_special, true);
    }
    return out;
}

// Devuelve solo cadenas UTF-8 completas: si un token termina a mitad de un
// caracter multibyte, se guarda el resto para el siguiente.
std::string complete_utf8(std::string & pending) {
    size_t n = pending.size();
    size_t cut = n;
    // buscar el inicio del ultimo caracter y ver si esta completo
    for (size_t i = n; i > 0 && i + 4 > n; --i) {
        unsigned char c = (unsigned char) pending[i - 1];
        if ((c & 0xC0) == 0x80) continue;          // byte de continuacion
        size_t need = (c >= 0xF0) ? 4 : (c >= 0xE0) ? 3 : (c >= 0xC0) ? 2 : 1;
        if (i - 1 + need > n) cut = i - 1;          // caracter incompleto
        break;
    }
    std::string out = pending.substr(0, cut);
    pending.erase(0, cut);
    return out;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_ferolabs_hablo_Llm_nativeLoad(JNIEnv * env, jobject, jstring jpath, jint n_ctx, jint n_threads) {
    if (g) return 0;
    llama_log_set(log_cb, nullptr);
    llama_backend_init();

    std::string path = jstr(env, jpath);
    llama_model_params mp = llama_model_default_params();
    // load_mode por defecto (mmap): el archivo de 5 GB se mapea, no se copia a RAM
    llama_model * model = llama_model_load_from_file(path.c_str(), mp);
    if (!model) {
        LOGE("no se pudo cargar %s", path.c_str());
        return 1;
    }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = n_ctx;
    cp.n_batch = 512;
    cp.n_ubatch = 512;
    cp.n_threads = n_threads;
    cp.n_threads_batch = n_threads;
    llama_context * ctx = llama_init_from_model(model, cp);
    if (!ctx) {
        LOGE("no se pudo crear el contexto");
        llama_model_free(model);
        return 2;
    }

    auto * e = new Engine();
    e->model = model;
    e->ctx = ctx;
    e->vocab = llama_model_get_vocab(model);
    e->n_ctx = (int) llama_n_ctx(ctx);

    // Penalizacion de repeticion: sin ella el modelo copia su frase anterior
    // turno tras turno (visto en la cafeteria: la misma respuesta ocho veces).
    llama_sampler_chain_params sp = llama_sampler_chain_default_params();
    e->sampler = llama_sampler_chain_init(sp);
    llama_sampler_chain_add(e->sampler, llama_sampler_init_penalties(llama_vocab_n_tokens(e->vocab), 128, 1.18f, 0.0f, 0.0f));
    llama_sampler_chain_add(e->sampler, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(e->sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(e->sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(e->sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    g = e;
    char desc[256];
    llama_model_desc(model, desc, sizeof(desc));
    LOGI("modelo cargado: %s | contexto %d | hilos %d | %s", desc, e->n_ctx, n_threads, llama_print_system_info());
    return 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ferolabs_hablo_Llm_nativeApplyTemplate(JNIEnv * env, jobject, jobjectArray roles, jobjectArray contents, jboolean add_ass) {
    if (!g) return env->NewStringUTF("");
    jsize n = env->GetArrayLength(roles);
    std::vector<std::string> r(n), c(n);
    std::vector<llama_chat_message> msgs(n);
    size_t total = 0;
    for (jsize i = 0; i < n; i++) {
        r[i] = jstr(env, (jstring) env->GetObjectArrayElement(roles, i));
        c[i] = jstr(env, (jstring) env->GetObjectArrayElement(contents, i));
        msgs[i] = { r[i].c_str(), c[i].c_str() };
        total += c[i].size() + r[i].size();
    }
    const char * tmpl = llama_model_chat_template(g->model, nullptr);
    std::vector<char> buf(total * 2 + 1024);
    int32_t len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), add_ass, buf.data(), (int32_t) buf.size());
    if (len < 0) {
        LOGE("la plantilla de chat del modelo no se pudo aplicar");
        return env->NewStringUTF("");
    }
    if ((size_t) len > buf.size()) {
        buf.resize(len + 1);
        len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), add_ass, buf.data(), (int32_t) buf.size());
    }
    return env->NewStringUTF(std::string(buf.data(), len).c_str());
}

// Conversacion por deltas: la memoria del modelo (KV cache) guarda todo lo
// procesado y lo generado; cada turno solo se le da el texto nuevo que la
// plantilla agrega al final. Nada se vuelve a procesar.

extern "C" JNIEXPORT void JNICALL
Java_com_ferolabs_hablo_Llm_nativeReset(JNIEnv *, jobject) {
    if (!g) return;
    llama_memory_clear(llama_get_memory(g->ctx), true);
    g->n_past = 0;
    g->answer_start = 0;
    g->generating = false;
    g->pending.clear();
}

// Procesa [text] (con sus tokens de control) a continuacion de lo que ya hay.
// Devuelve cuantos tokens proceso, o -3 si no cabe en el contexto.
extern "C" JNIEXPORT jint JNICALL
Java_com_ferolabs_hablo_Llm_nativeFeed(JNIEnv * env, jobject, jstring jtext) {
    if (!g) return -1;
    std::string text = jstr(env, jtext);
    if (text.empty()) return 0;
    // add_special=false: el BOS lo pone la plantilla si hace falta; parse_special=true
    // para que <|im_start|> y compania sean tokens de control, no texto.
    std::vector<llama_token> toks = tokenize(g->vocab, text, false);
    if (toks.empty()) return 0;
    if (g->n_past + (int) toks.size() >= g->n_ctx - 64) {
        LOGE("no caben %zu tokens mas en el contexto (%d de %d)", toks.size(), g->n_past, g->n_ctx);
        return -3;
    }
    const int n_batch = 512;
    for (size_t i = 0; i < toks.size(); i += n_batch) {
        int n = std::min((size_t) n_batch, toks.size() - i);
        llama_batch batch = llama_batch_get_one(toks.data() + i, n);
        if (llama_decode(g->ctx, batch) != 0) {
            LOGE("llama_decode fallo procesando texto");
            return -4;
        }
        g->n_past += n;
    }
    return (jint) toks.size();
}

extern "C" JNIEXPORT void JNICALL
Java_com_ferolabs_hablo_Llm_nativeBeginAnswer(JNIEnv *, jobject) {
    if (!g) return;
    g->answer_start = g->n_past;
    g->pending.clear();
    llama_sampler_reset(g->sampler);
    g->generating = true;
}

// Descarta lo generado desde nativeBeginAnswer (para reintentar).
extern "C" JNIEXPORT void JNICALL
Java_com_ferolabs_hablo_Llm_nativeDiscardAnswer(JNIEnv *, jobject) {
    if (!g) return;
    llama_memory_seq_rm(llama_get_memory(g->ctx), 0, (llama_pos) g->answer_start, -1);
    g->n_past = g->answer_start;
    g->generating = false;
    g->pending.clear();
}

// Siguiente trozo de texto, o null cuando el modelo termino (o no cabe mas).
extern "C" JNIEXPORT jstring JNICALL
Java_com_ferolabs_hablo_Llm_nativeNext(JNIEnv * env, jobject) {
    if (!g || !g->generating) return nullptr;
    if (g->n_past >= g->n_ctx - 1) {
        g->generating = false;
        return nullptr;
    }
    llama_token tok = llama_sampler_sample(g->sampler, g->ctx, -1);
    llama_sampler_accept(g->sampler, tok);
    if (llama_vocab_is_eog(g->vocab, tok)) {
        g->generating = false;
        return nullptr;
    }
    // special=false: los tokens de control (<think>, <|im_end|>...) no se
    // convierten en texto; solo las palabras.
    char piece[256];
    int n = llama_token_to_piece(g->vocab, tok, piece, sizeof(piece), 0, false);
    if (n > 0) g->pending.append(piece, n);

    llama_batch batch = llama_batch_get_one(&tok, 1);
    if (llama_decode(g->ctx, batch) != 0) {
        LOGE("llama_decode fallo generando");
        g->generating = false;
        return nullptr;
    }
    g->n_past += 1;

    std::string out = complete_utf8(g->pending);
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_ferolabs_hablo_Llm_nativeStop(JNIEnv *, jobject) {
    if (g) g->generating = false;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ferolabs_hablo_Llm_nativeFree(JNIEnv *, jobject) {
    if (!g) return;
    llama_sampler_free(g->sampler);
    llama_free(g->ctx);
    llama_model_free(g->model);
    delete g;
    g = nullptr;
    LOGI("modelo liberado");
}
