#include <jni.h>
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"
#include <chrono>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>
#include <mutex>

namespace {
using Clock = std::chrono::steady_clock;
struct Brain {
    llama_model *model = nullptr;
    llama_context *context = nullptr;
    mtmd_context *vision = nullptr;
    Clock::time_point deadline = Clock::time_point::max();
    ~Brain() { if (vision) mtmd_free(vision); if (context) llama_free(context); if (model) llama_model_free(model); }
};
bool expired(void *ptr) { return Clock::now() >= static_cast<Brain *>(ptr)->deadline; }
std::string bytes(JNIEnv *env, jbyteArray array) {
    const auto size = env->GetArrayLength(array);
    std::string out(size, '\0');
    env->GetByteArrayRegion(array, 0, size, reinterpret_cast<jbyte *>(out.data()));
    return out;
}
void fail(JNIEnv *env, const char *message) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message); }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_niki914_zafiro_app_localai_GuiOwlNative_open(JNIEnv *env, jobject, jbyteArray modelPath, jbyteArray visionPath) {
    try {
        static std::once_flag initialized;
        std::call_once(initialized, [] { llama_backend_init(); });
        auto brain = std::make_unique<Brain>();
        auto mp = llama_model_default_params(); mp.n_gpu_layers = 0;
        brain->model = llama_model_load_from_file(bytes(env, modelPath).c_str(), mp);
        if (!brain->model) throw std::runtime_error("GUI_OWL_MODEL_LOAD_FAILED");
        auto cp = llama_context_default_params();
        cp.n_ctx = 2048; cp.n_batch = 256; cp.n_ubatch = 128;
        cp.n_threads = 2; cp.n_threads_batch = 2;
        cp.abort_callback = expired; cp.abort_callback_data = brain.get();
        brain->context = llama_init_from_model(brain->model, cp);
        if (!brain->context) throw std::runtime_error("GUI_OWL_CONTEXT_FAILED");
        auto vp = mtmd_context_params_default();
        vp.use_gpu = false; vp.n_threads = 2; vp.warmup = false;
        vp.image_min_tokens = 64; vp.image_max_tokens = 512;
        brain->vision = mtmd_init_from_file(bytes(env, visionPath).c_str(), brain->model, vp);
        if (!brain->vision || !mtmd_support_vision(brain->vision)) throw std::runtime_error("GUI_OWL_VISION_LOAD_FAILED");
        return reinterpret_cast<jlong>(brain.release());
    } catch (const std::exception &error) { fail(env, error.what()); return 0; }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_niki914_zafiro_app_localai_GuiOwlNative_infer(JNIEnv *env, jobject, jlong handle, jbyteArray promptBytes,
                                                    jbyteArray rgbBytes, jint width, jint height, jint budgetMs) {
    try {
        auto *brain = reinterpret_cast<Brain *>(handle);
        if (!brain || width <= 0 || height <= 0 || width > 2048 || height > 2048 ||
            env->GetArrayLength(rgbBytes) != width * height * 3) throw std::runtime_error("GUI_OWL_INVALID_INPUT");
        brain->deadline = Clock::now() + std::chrono::milliseconds(budgetMs);
        const auto started = Clock::now();
        long first_output_ms = -1;
        llama_memory_clear(llama_get_memory(brain->context), true);
        const auto rgb = bytes(env, rgbBytes);
        using Bitmap = std::unique_ptr<mtmd_bitmap, decltype(&mtmd_bitmap_free)>;
        Bitmap image(mtmd_bitmap_init(width, height, reinterpret_cast<const unsigned char *>(rgb.data())), mtmd_bitmap_free);
        const auto user = std::string(mtmd_get_marker(brain->vision)) + "\n" + bytes(env, promptBytes);
        llama_chat_message message{"user", user.c_str()};
        const char *tmpl = llama_model_chat_template(brain->model, nullptr);
        int count = llama_chat_apply_template(tmpl, &message, 1, true, nullptr, 0);
        if (count <= 0 || count > 64000) throw std::runtime_error("GUI_OWL_CHAT_TEMPLATE_FAILED");
        std::vector<char> formatted(count + 1);
        if (llama_chat_apply_template(tmpl, &message, 1, true, formatted.data(), formatted.size()) != count)
            throw std::runtime_error("GUI_OWL_CHAT_TEMPLATE_FAILED");
        mtmd_input_text text{formatted.data(), static_cast<size_t>(count), true, true};
        using Chunks = std::unique_ptr<mtmd_input_chunks, decltype(&mtmd_input_chunks_free)>;
        Chunks chunks(mtmd_input_chunks_init(), mtmd_input_chunks_free);
        const mtmd_bitmap *images[] = {image.get()};
        if (mtmd_tokenize(brain->vision, chunks.get(), &text, images, 1)) throw std::runtime_error("GUI_OWL_IMAGE_TOKENIZE_FAILED");
        if (mtmd_helper_get_n_tokens(chunks.get()) + 256 > llama_n_ctx(brain->context)) throw std::runtime_error("GUI_OWL_CONTEXT_LIMIT");
        llama_pos position = 0;
        if (mtmd_helper_eval_chunks(brain->vision, brain->context, chunks.get(), 0, 0, 256, true, &position))
            throw std::runtime_error("GUI_OWL_IMAGE_INFERENCE_FAILED");
        using Sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>;
        Sampler sampler(llama_sampler_init_greedy(), llama_sampler_free);
        const auto *vocab = llama_model_get_vocab(brain->model);
        std::string output;
        using Batch = std::unique_ptr<llama_batch_ext, decltype(&llama_batch_ext_free)>;
        Batch batch(llama_batch_ext_init(brain->context), llama_batch_ext_free);
        if (!batch) throw std::runtime_error("GUI_OWL_BATCH_FAILED");
        try {
            for (int i = 0; i < 256; ++i) {
                if (expired(brain)) throw std::runtime_error("GUI_OWL_TIMEOUT");
                const auto token = llama_sampler_sample(sampler.get(), brain->context, -1);
                if (llama_vocab_is_eog(vocab, token)) break;
                if (first_output_ms < 0) first_output_ms = std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now() - started).count();
                char piece[512];
                const int size = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
                if (size < 0) throw std::runtime_error("GUI_OWL_TOKEN_LIMIT");
                output.append(piece, size);
                llama_batch_ext_clear(batch.get());
                const auto idx = llama_batch_ext_add_token(batch.get(), 0, token);
                if (idx < 0 || !llama_batch_ext_set_pos(batch.get(), idx, &position) ||
                    !llama_batch_ext_set_output_logits(batch.get(), idx, true)) throw std::runtime_error("GUI_OWL_BATCH_FAILED");
                ++position;
                if (llama_process(brain->context, LLAMA_PROCESS_TYPE_DECODE, batch.get())) throw std::runtime_error("GUI_OWL_DECODE_FAILED");
            }
        } catch (...) { throw; }
        if (first_output_ms < 0) throw std::runtime_error("GUI_OWL_EMPTY_OUTPUT");
        output = std::to_string(first_output_ms) + "\n" + output;
        auto result = env->NewByteArray(output.size());
        if (result) env->SetByteArrayRegion(result, 0, output.size(), reinterpret_cast<const jbyte *>(output.data()));
        return result;
    } catch (const std::exception &error) { fail(env, error.what()); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_niki914_zafiro_app_localai_GuiOwlNative_close(JNIEnv *, jobject, jlong handle) { delete reinterpret_cast<Brain *>(handle); }
