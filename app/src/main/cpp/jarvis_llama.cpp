#include <jni.h>
#include <android/log.h>

#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>
#include <utility>
#include <vector>

namespace {

constexpr const char * kTag = "JarvisLlama";
constexpr int32_t kContextSize = 2048;
constexpr int32_t kBatchSize = 512;
constexpr int32_t kMaxGenerationTokens = 512;

std::mutex g_mutex;
std::atomic<bool> g_cancelled{false};
llama_model * g_model = nullptr;
llama_context * g_context = nullptr;
llama_sampler * g_sampler = nullptr;
mtmd_context * g_mmproj = nullptr;
int32_t g_generated_tokens = 0;
bool g_native_eos = false;
std::string g_pending_utf8;

void throw_java(JNIEnv * env, const char * type, const std::string & message) {
    jclass exception = env->FindClass(type);
    if (exception != nullptr) {
        env->ThrowNew(exception, message.c_str());
    }
}

std::string from_java(JNIEnv * env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

size_t complete_utf8_prefix(const std::string & value) {
    size_t offset = 0;
    while (offset < value.size()) {
        const auto first = static_cast<unsigned char>(value[offset]);
        size_t length = first < 0x80 ? 1 :
            (first >= 0xC2 && first <= 0xDF) ? 2 :
            (first >= 0xE0 && first <= 0xEF) ? 3 :
            (first >= 0xF0 && first <= 0xF4) ? 4 : 1;
        if (offset + length > value.size()) break;
        bool valid = true;
        for (size_t index = 1; index < length; ++index) {
            valid &= (static_cast<unsigned char>(value[offset + index]) & 0xC0) == 0x80;
        }
        offset += valid ? length : 1;
    }
    return offset;
}

jstring to_java_string(JNIEnv * env, const std::string & value) {
    std::vector<jchar> utf16;
    utf16.reserve(value.size());
    for (size_t offset = 0; offset < value.size();) {
        const auto first = static_cast<unsigned char>(value[offset]);
        uint32_t codepoint = 0xFFFD;
        size_t length = 1;
        if (first < 0x80) {
            codepoint = first;
        } else {
            const size_t candidate = (first >= 0xC2 && first <= 0xDF) ? 2 :
                (first >= 0xE0 && first <= 0xEF) ? 3 :
                (first >= 0xF0 && first <= 0xF4) ? 4 : 0;
            if (candidate != 0 && offset + candidate <= value.size()) {
                codepoint = first & ((1u << (7 - candidate)) - 1u);
                bool valid = true;
                for (size_t index = 1; index < candidate; ++index) {
                    const auto next = static_cast<unsigned char>(value[offset + index]);
                    valid &= (next & 0xC0) == 0x80;
                    codepoint = (codepoint << 6) | (next & 0x3F);
                }
                valid &= !(candidate == 3 && codepoint < 0x800);
                valid &= !(candidate == 4 && codepoint < 0x10000);
                valid &= codepoint <= 0x10FFFF && !(codepoint >= 0xD800 && codepoint <= 0xDFFF);
                if (valid) length = candidate;
                else codepoint = 0xFFFD;
            }
        }
        if (codepoint <= 0xFFFF) {
            utf16.push_back(static_cast<jchar>(codepoint));
        } else {
            codepoint -= 0x10000;
            utf16.push_back(static_cast<jchar>(0xD800 + (codepoint >> 10)));
            utf16.push_back(static_cast<jchar>(0xDC00 + (codepoint & 0x3FF)));
        }
        offset += length;
    }
    return env->NewString(utf16.data(), static_cast<jsize>(utf16.size()));
}

void release_locked() {
    if (g_sampler != nullptr) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_mmproj != nullptr) {
        mtmd_free(g_mmproj);
        g_mmproj = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

llama_context * create_context() {
    llama_context_params params = llama_context_default_params();
    params.n_ctx = kContextSize;
    params.n_batch = kBatchSize;
    params.n_ubatch = kBatchSize;
    const int threads = std::max(2, static_cast<int>(std::thread::hardware_concurrency()) - 1);
    params.n_threads = threads;
    params.n_threads_batch = threads;
    return llama_init_from_model(g_model, params);
}

bool decode_tokens(const std::vector<llama_token> & tokens) {
    for (size_t offset = 0; offset < tokens.size(); offset += kBatchSize) {
        const size_t count = std::min(static_cast<size_t>(kBatchSize), tokens.size() - offset);
        llama_batch batch = llama_batch_init(static_cast<int32_t>(count), 0, 1);
        batch.n_tokens = static_cast<int32_t>(count);
        for (int32_t index = 0; index < batch.n_tokens; ++index) {
            batch.token[index] = tokens[offset + index];
            batch.pos[index] = static_cast<llama_pos>(offset + index);
            batch.n_seq_id[index] = 1;
            batch.seq_id[index][0] = 0;
            batch.logits[index] = index == batch.n_tokens - 1 && offset + count == tokens.size();
        }
        const int result = llama_decode(g_context, batch);
        llama_batch_free(batch);
        if (result != 0) {
            return false;
        }
    }
    return true;
}

std::string make_prompt(JNIEnv * env, jobjectArray roles, jobjectArray contents, bool has_image) {
    const jsize count = env->GetArrayLength(roles);
    if (count == 0 || count != env->GetArrayLength(contents)) {
        throw_java(env, "java/lang/IllegalArgumentException", "The conversation is empty or malformed.");
        return {};
    }

    std::vector<std::string> role_values;
    std::vector<std::string> content_values;
    std::vector<llama_chat_message> messages;
    role_values.reserve(count);
    content_values.reserve(count);
    messages.reserve(count);

    for (jsize index = 0; index < count; ++index) {
        auto role = static_cast<jstring>(env->GetObjectArrayElement(roles, index));
        auto content = static_cast<jstring>(env->GetObjectArrayElement(contents, index));
        role_values.push_back(from_java(env, role));
        content_values.push_back(from_java(env, content));
        if (role != nullptr) env->DeleteLocalRef(role);
        if (content != nullptr) env->DeleteLocalRef(content);
    }
    if (has_image) {
        if (content_values.back().empty()) {
            content_values.back() = "<__media__>";
        } else {
            content_values.back() += "\n<__media__>";
        }
    }
    for (size_t index = 0; index < role_values.size(); ++index) {
        messages.push_back({role_values[index].c_str(), content_values[index].c_str()});
    }

    const char * chat_template = llama_model_chat_template(g_model, nullptr);
    if (chat_template == nullptr) {
        throw_java(env, "java/lang/IllegalArgumentException",
            "This GGUF has no embedded chat template supported by llama.cpp.");
        return {};
    }
    size_t capacity = 4096;
    for (const auto & content : content_values) capacity += content.size() * 2;
    std::vector<char> output(capacity);
    int32_t written = llama_chat_apply_template(chat_template, messages.data(), messages.size(), true,
        output.data(), static_cast<int32_t>(output.size()));
    if (written < 0 || static_cast<size_t>(written) >= output.size()) {
        throw_java(env, "java/lang/IllegalArgumentException", "The model chat template could not format this conversation.");
        return {};
    }
    return std::string(output.data(), static_cast<size_t>(written));
}

std::vector<llama_token> tokenize(const std::string & prompt) {
    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    std::vector<llama_token> tokens(prompt.size() + 32);
    int32_t count = llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
        tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (count < 0) {
        tokens.resize(static_cast<size_t>(-count));
        count = llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    }
    if (count < 0) return {};
    tokens.resize(static_cast<size_t>(count));
    return tokens;
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_jarvis_localai_inference_LlamaCppEngine_nativeLoadModel(JNIEnv * env, jobject,
        jstring model_path, jstring projector_path) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_locked();
    g_cancelled.store(false);
    g_native_eos = false;
    g_pending_utf8.clear();
    const std::string model_file = from_java(env, model_path);
    const std::string projector_file = from_java(env, projector_path);
    if (model_file.empty()) {
        return env->NewStringUTF("Select a local GGUF model file first.");
    }

    llama_backend_init();
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(model_file.c_str(), model_params);
    if (g_model == nullptr) {
        return env->NewStringUTF("llama.cpp could not load this GGUF. Check that it is a supported architecture and that enough memory is available.");
    }
    g_context = create_context();
    if (g_context == nullptr) {
        release_locked();
        return env->NewStringUTF("The model loaded, but a 2048-token context could not be allocated. Try a smaller quantization or free device memory.");
    }
    if (!projector_file.empty()) {
        mtmd_context_params mm_params = mtmd_context_params_default();
        mm_params.use_gpu = false;
        mm_params.n_threads = std::max(2, static_cast<int>(std::thread::hardware_concurrency()) - 1);
        g_mmproj = mtmd_init_from_file(projector_file.c_str(), g_model, mm_params);
        if (g_mmproj == nullptr || !mtmd_support_vision(g_mmproj)) {
            release_locked();
            return env->NewStringUTF("The projector is incompatible with this model or does not provide vision support. Import the matching mmproj GGUF from the same model release.");
        }
    }
    __android_log_print(ANDROID_LOG_INFO, kTag, "Loaded llama.cpp GGUF; vision=%s", g_mmproj ? "yes" : "no");
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jarvis_localai_inference_LlamaCppEngine_nativeStartGeneration(JNIEnv * env, jobject,
        jobjectArray roles, jobjectArray contents, jbyteArray image_rgb, jint width, jint height) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_cancelled.store(false);
    g_generated_tokens = 0;
    g_native_eos = false;
    g_pending_utf8.clear();
    if (g_model == nullptr) return env->NewStringUTF("Load a compatible model before sending a message.");
    if (g_sampler != nullptr) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    g_context = create_context();
    if (g_context == nullptr) return env->NewStringUTF("Could not allocate the model context. Reduce memory pressure and retry.");

    const bool has_image = image_rgb != nullptr;
    if (has_image && (g_mmproj == nullptr || !mtmd_support_vision(g_mmproj))) {
        return env->NewStringUTF("The active model is text-only. Load a compatible model and matching vision projector to analyze images.");
    }
    std::string prompt = make_prompt(env, roles, contents, has_image);
    if (env->ExceptionCheck()) return nullptr;
    const auto tokens = tokenize(prompt);
    if (tokens.empty()) return env->NewStringUTF("The conversation could not be tokenized by this model.");

    int decode_result = 0;
    if (has_image) {
        const jsize byte_count = env->GetArrayLength(image_rgb);
        if (width <= 0 || height <= 0 || static_cast<int64_t>(width) * height * 3 != byte_count) {
            return env->NewStringUTF("The preprocessed image buffer is invalid.");
        }
        std::vector<unsigned char> pixels(static_cast<size_t>(byte_count));
        env->GetByteArrayRegion(image_rgb, 0, byte_count, reinterpret_cast<jbyte *>(pixels.data()));
        mtmd_bitmap * bitmap = mtmd_bitmap_init(static_cast<uint32_t>(width), static_cast<uint32_t>(height), pixels.data());
        if (bitmap == nullptr) return env->NewStringUTF("Could not create an image tensor from the selected photo.");
        mtmd_input_chunks * chunks = mtmd_input_chunks_init();
        const std::string image_prompt = prompt;
        mtmd_input_text input_text{image_prompt.c_str(), image_prompt.size(), true, true};
        const mtmd_bitmap * bitmaps[] = {bitmap};
        const int result = mtmd_tokenize(g_mmproj, chunks, &input_text, bitmaps, 1);
        if (result == 0) {
            if (mtmd_helper_get_n_pos(chunks) > kContextSize) {
                decode_result = 1;
            } else {
                llama_pos new_position = 0;
                decode_result = mtmd_helper_eval_chunks(g_mmproj, g_context, chunks, 0, 0, kBatchSize, true, &new_position);
            }
        } else {
            decode_result = result;
        }
        mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(bitmap);
    } else if (tokens.size() > kContextSize) {
        return env->NewStringUTF("This conversation exceeds the 2048-token context. Start a new chat or shorten its history.");
    } else if (!decode_tokens(tokens)) {
        decode_result = 1;
    }
    if (decode_result != 0) {
        return env->NewStringUTF("llama.cpp could not evaluate the prompt. Check the model/projector pairing and available memory.");
    }

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(0));
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jarvis_localai_inference_LlamaCppEngine_nativeNextToken(JNIEnv * env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model == nullptr || g_context == nullptr || g_sampler == nullptr || g_cancelled.load()) {
        return nullptr;
    }
    if (g_native_eos || g_generated_tokens >= kMaxGenerationTokens) {
        g_native_eos = true;
        if (g_pending_utf8.empty()) return nullptr;
        std::string tail = std::move(g_pending_utf8);
        g_pending_utf8.clear();
        return to_java_string(env, tail);
    }
    ++g_generated_tokens;
    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    const llama_token token = llama_sampler_sample(g_sampler, g_context, -1);
    llama_sampler_accept(g_sampler, token);
    if (llama_vocab_is_eog(vocab, token)) {
        g_native_eos = true;
        if (g_pending_utf8.empty()) return nullptr;
        std::string tail = std::move(g_pending_utf8);
        g_pending_utf8.clear();
        return to_java_string(env, tail);
    }

    char piece[256];
    const int32_t length = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, false);
    if (length <= 0) return nullptr;
    llama_batch batch = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
    if (llama_decode(g_context, batch) != 0) {
        throw_java(env, "java/lang/IllegalStateException", "Native token decoding failed; the context may be out of memory.");
        return nullptr;
    }
    g_pending_utf8.append(piece, static_cast<size_t>(length));
    const size_t complete = complete_utf8_prefix(g_pending_utf8);
    if (complete == 0) return env->NewStringUTF("");
    std::string output = g_pending_utf8.substr(0, complete);
    g_pending_utf8.erase(0, complete);
    return to_java_string(env, output);
}

extern "C" JNIEXPORT void JNICALL
Java_com_jarvis_localai_inference_LlamaCppEngine_nativeCancelGeneration(JNIEnv *, jobject) {
    g_cancelled.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_jarvis_localai_inference_LlamaCppEngine_nativeUnloadModel(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_cancelled.store(true);
    release_locked();
    llama_backend_free();
}