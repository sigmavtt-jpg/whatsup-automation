#include <jni.h>
#include <string>
#include <cstdlib>
#include <cstring>
#include <unistd.h>
#include <pthread.h>
#include <android/log.h>

#define LOG_TAG "NodeRunner"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace node {
    int Start(int argc, char** argv);
}

// Redirect stdout and stderr from Node.js (V8) to Android logcat
static int pfd[2];
static pthread_t logger_thr;
static bool is_redirected = false;

static void* logger_thread_func(void*) {
    ssize_t rdsz;
    char buf[2048];
    while ((rdsz = read(pfd[0], buf, sizeof(buf) - 1)) > 0) {
        if (buf[rdsz - 1] == '\n') --rdsz;
        buf[rdsz] = 0;
        __android_log_print(ANDROID_LOG_INFO, "NodeJS", "%s", buf);
    }
    return nullptr;
}

static void start_redirecting_stdout_and_stderr() {
    if (is_redirected) return;
    is_redirected = true;

    // NOTE: DO NOT call setvbuf on Android Bionic libc!
    // setvbuf corrupts stdout's internal pthread_mutex on Android 13/14 FORTIFY.
    if (pipe(pfd) == -1) {
        LOGE("Failed to create pipe for stdout/stderr redirection");
        return;
    }

    if (dup2(pfd[1], STDOUT_FILENO) == -1 || dup2(pfd[1], STDERR_FILENO) == -1) {
        LOGE("Failed to dup2 stdout/stderr to pipe");
        return;
    }

    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    if (pthread_create(&logger_thr, &attr, logger_thread_func, nullptr) == 0) {
        LOGI("Successfully redirected stdout and stderr to logcat tag: NodeJS");
    } else {
        LOGE("Failed to create logger thread");
    }
    pthread_attr_destroy(&attr);
}

struct NodeWorkerArgs {
    int argc;
    char** argv;
    char* args_buffer;
};

static void* node_worker_func(void* param) {
    auto* args = static_cast<NodeWorkerArgs*>(param);
    LOGI("Node worker thread started, calling node::Start with %d args...", args->argc);
    for (int i = 0; i < args->argc; i++) {
        LOGI("  argv[%d] = %s", i, args->argv[i]);
    }

    int node_result = node::Start(args->argc, args->argv);
    LOGI("node::Start finished with exit code: %d", node_result);

    free(args->args_buffer);
    free(args->argv);
    delete args;
    return nullptr;
}

// Spawns node::Start on a dedicated native pthread with 4MB stack size
extern "C" JNIEXPORT jint JNICALL
Java_com_whatsup_automation_data_engine_NodeRunner_startNodeWithArguments(
        JNIEnv *env,
        jclass clazz,
        jobjectArray arguments) {

    start_redirecting_stdout_and_stderr();

    jsize argument_count = env->GetArrayLength(arguments);

    // Compute byte size needed for all arguments in contiguous memory.
    int c_arguments_size = 0;
    for (int i = 0; i < argument_count; i++) {
        jstring argument = (jstring) env->GetObjectArrayElement(arguments, i);
        const char* current_argument = env->GetStringUTFChars(argument, 0);
        c_arguments_size += strlen(current_argument) + 1;
        env->ReleaseStringUTFChars(argument, current_argument);
    }

    // Allocate contiguous memory for strings
    char* args_buffer = (char*) calloc(c_arguments_size, sizeof(char));
    if (!args_buffer) {
        LOGE("Failed to allocate args_buffer of size %d", c_arguments_size);
        return -1;
    }

    // Allocate memory for argv array
    char** argv = (char**) malloc(argument_count * sizeof(char*));
    if (!argv) {
        LOGE("Failed to allocate argv array of size %d", argument_count);
        free(args_buffer);
        return -1;
    }

    // Copy strings into contiguous memory and set argv pointers
    char* current_args_position = args_buffer;
    for (int i = 0; i < argument_count; i++) {
        jstring argument = (jstring) env->GetObjectArrayElement(arguments, i);
        const char* current_argument = env->GetStringUTFChars(argument, 0);
        size_t len = strlen(current_argument);
        strncpy(current_args_position, current_argument, len);
        current_args_position[len] = '\0';
        argv[i] = current_args_position;
        current_args_position += len + 1;
        env->ReleaseStringUTFChars(argument, current_argument);
    }

    auto* worker_args = new NodeWorkerArgs{
        .argc = argument_count,
        .argv = argv,
        .args_buffer = args_buffer
    };

    // Run Node in a dedicated native pthread with 4MB stack size
    pthread_t node_thread;
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, 4 * 1024 * 1024);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);

    int create_res = pthread_create(&node_thread, &attr, node_worker_func, worker_args);
    pthread_attr_destroy(&attr);

    if (create_res != 0) {
        LOGE("Failed to create dedicated Node pthread: error %d", create_res);
        free(args_buffer);
        free(argv);
        delete worker_args;
        return -1;
    }

    LOGI("Node.js worker thread dispatched successfully.");
    return 0;
}
