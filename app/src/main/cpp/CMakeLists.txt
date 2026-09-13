# Motor de IA de la Fase 3: llama.cpp compilado desde el codigo fuente dentro
# de la app (lo descarga la tarea Gradle fetchLlamaCpp a cpp/llama.cpp/), mas
# un puente JNI minimo propio (llm_jni.cpp). Sin AARs de terceros.
#
# Todo estatico dentro de una sola libhablo-llm.so: menos piezas que carguen
# mal. Solo arm64-v8a (el S25 Ultra); ver abiFilters en build.gradle.kts.

cmake_minimum_required(VERSION 3.22)
project("hablo-llm" LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/llama.cpp)
if(NOT EXISTS ${LLAMA_SRC}/CMakeLists.txt)
    message(FATAL_ERROR "No esta el codigo de llama.cpp en ${LLAMA_SRC}. Corre la tarea Gradle fetchLlamaCpp.")
endif()

# --- Opciones de llama.cpp / ggml -------------------------------------------
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER     OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL             OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL          OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL        OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE         OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            ON  CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI      OFF CACHE BOOL "" FORCE)
# Snapdragon 8 Elite (Oryon): dotprod, i8mm y fp16 nativos. Un solo juego de
# kernels; un telefono sin i8mm no arrancaria el motor (no es nuestro caso).
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+i8mm+fp16" CACHE STRING "" FORCE)

add_subdirectory(${LLAMA_SRC} build-llama EXCLUDE_FROM_ALL)

add_library(hablo-llm SHARED llm_jni.cpp)

target_include_directories(hablo-llm PRIVATE
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include)

target_link_libraries(hablo-llm PRIVATE llama android log)
