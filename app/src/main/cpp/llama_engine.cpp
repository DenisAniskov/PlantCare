#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include <chrono>
#ifdef ENABLE_LLAMA
#include "llama.h"
#endif

static std::string g_model_path;
#ifdef ENABLE_LLAMA
static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static const llama_vocab * g_vocab = nullptr;
#endif

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_plantcare_ai_LlamaEngineNative_initModel(
        JNIEnv* env,
        jobject /*thiz*/,
        jstring jmodelPath
) {
    const char* c = jmodelPath ? env->GetStringUTFChars(jmodelPath, nullptr) : nullptr;
    if (!c) return JNI_FALSE;
    g_model_path = std::string(c);
    env->ReleaseStringUTFChars(jmodelPath, c);
#ifdef ENABLE_LLAMA
    // init backend once
    llama_backend_init();
    // unload previous
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }

    llama_model_params mparams = llama_model_default_params();
    mparams.use_mmap = true;
    mparams.use_mlock = false;
    g_model = llama_model_load_from_file(g_model_path.c_str(), mparams);
    if (!g_model) {
        return JNI_FALSE;
    }
    g_vocab = llama_model_get_vocab(g_model);
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 2048;
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;
    cparams.n_batch = 512;   // logical max tokens per llama_decode
    cparams.n_ubatch = 64;   // physical chunk size
    cparams.n_seq_max = 1;   // single sequence
    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        llama_model_free(g_model); g_model = nullptr;
        return JNI_FALSE;
    }
    llama_set_n_threads(g_ctx, 4, 4);
#endif
    // For now, just check non-empty path (stub)
    return g_model_path.empty() ? JNI_FALSE : JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_example_plantcare_ai_LlamaEngineNative_nativeGenerate(
        JNIEnv* env,
        jobject /*thiz*/,
        jstring jprompt,
        jint jmaxTokens,
        jfloat /*temperature*/
) {
    const char* c = jprompt ? env->GetStringUTFChars(jprompt, nullptr) : nullptr;
    std::string prompt = c ? std::string(c) : std::string();
    if (c) env->ReleaseStringUTFChars(jprompt, c);
    if (prompt.size() > 400) prompt = prompt.substr(0, 400);
#ifdef ENABLE_LLAMA
    if (!g_model || !g_ctx) {
        std::string out = std::string("[llama.cpp not ready] model=") + g_model_path + "; " + prompt;
        return env->NewStringUTF(out.c_str());
    }
    // tokenize
    std::vector<llama_token> tokens;
    tokens.resize(prompt.size() + 8);
    int n_toks = llama_tokenize(g_vocab, prompt.c_str(), (int)prompt.size(), tokens.data(), (int)tokens.size(), true, true);
    if (n_toks < 0) {
        // need more space
        tokens.resize((size_t)(-n_toks));
        n_toks = llama_tokenize(g_vocab, prompt.c_str(), (int)prompt.size(), tokens.data(), (int)tokens.size(), true, true);
    }
    tokens.resize(std::max(0, n_toks));
    if (tokens.empty()) {
        tokens.resize(1);
        tokens[0] = llama_vocab_bos(g_vocab);
    }

    // helper: safe eval via llama_batch_get_one (auto-pos/seq/logits)
    auto eval_tokens = [&](const llama_token * tk, int count) -> int {
        for (int i = 0; i < count; ++i) {
            llama_token t = tk[i];
            llama_batch batch = llama_batch_get_one(&t, 1);
            int rc = llama_decode(g_ctx, batch);
            if (rc != 0) return rc;
        }
        return 0;
    };

    int n_threads = 4;
    int n_past = 0;
    const int max_ctx = llama_n_ctx(g_ctx);
    if ((int)tokens.size() >= max_ctx - 8) {
        tokens.resize(std::max(1, max_ctx - 8));
    }
    {
        int rc = eval_tokens(tokens.data(), (int)tokens.size());
        if (rc != 0) {
            std::string out = std::string("[llama.decode rc=") + std::to_string(rc) + "]";
            return env->NewStringUTF(out.c_str());
        }
    }
    n_past += (int)tokens.size();

    // generate tokens greedily
    std::string out_text;
    const int n_vocab = llama_vocab_n_tokens(g_vocab);
    const llama_token eos = llama_vocab_eos(g_vocab);
    const int max_new = std::max(1, (int) jmaxTokens);
    auto t_start = std::chrono::steady_clock::now();
    std::vector<llama_token> recent;
    recent.reserve(64);
    const float rep_penalty = 0.8f; // simple repetition penalty
    for (int i = 0; i < max_new; ++i) {
        const float * logits = llama_get_logits(g_ctx);
        int next_id = 0;
        float best = logits[0] - (std::find(recent.begin(), recent.end(), 0) != recent.end() ? rep_penalty : 0.0f);
        for (int id = 1; id < n_vocab; ++id) {
            float score = logits[id] - (std::find(recent.begin(), recent.end(), id) != recent.end() ? rep_penalty : 0.0f);
            if (score > best) { best = score; next_id = id; }
        }
        if (next_id == eos) break;
        llama_token tok = (llama_token) next_id;
        // append piece
        char buf[512];
        int len = llama_token_to_piece(g_vocab, tok, buf, (int)sizeof(buf), 0, true);
        if (len > 0) out_text.append(buf, (size_t)len);
        // decode the token using batch helper
        if (eval_tokens(&tok, 1) != 0) break;
        n_past += 1;
        // track recent tokens (bounded)
        recent.push_back(tok);
        if ((int)recent.size() > 64) recent.erase(recent.begin());

        // early stop: end on sentence boundary or newline
        if (!out_text.empty()) {
            char last = out_text.back();
            if ((last == '.' || last == '!' || last == '?' || last == '\n') && out_text.size() > 24) {
                break;
            }
        }
        // wall-clock time budget (~6s) to avoid UI timeouts
        auto now = std::chrono::steady_clock::now();
        auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(now - t_start).count();
        if (ms > 6000) {
            break;
        }
    }
    if (out_text.empty()) out_text = "";
#else
    std::string out = std::string("[llama.cpp JNI заглушка] model=") + g_model_path + "; " + prompt;
    return env->NewStringUTF(out.c_str());
#endif
    return env->NewStringUTF(out_text.c_str());
}
}
