cmake_minimum_required(VERSION 3.22.1)
project(llama_engine)

option(ENABLE_LLAMA "Build with llama.cpp backend" OFF)

add_library(
        llama_engine
        SHARED
        llama_engine.cpp)

target_compile_definitions(llama_engine PRIVATE $<$<BOOL:${ENABLE_LLAMA}>:ENABLE_LLAMA=1>)

if(ENABLE_LLAMA)
    include(FetchContent)
    # Pin a known stable commit of llama.cpp
    set(LLAMA_CPP_GIT_TAG  "master" CACHE STRING "llama.cpp commit")
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(GGML_CUDA OFF CACHE BOOL "" FORCE)
    set(GGML_METAL OFF CACHE BOOL "" FORCE)
    set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
    set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
    set(GGML_BLAS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    FetchContent_Declare(
        llama_cpp
        GIT_REPOSITORY https://github.com/ggerganov/llama.cpp.git
        GIT_TAG ${LLAMA_CPP_GIT_TAG}
        GIT_SHALLOW TRUE
    )
    FetchContent_MakeAvailable(llama_cpp)
    # Link against llama target if available
    # Newer versions export target 'llama'; older may export 'ggml' and 'llama'
    if(TARGET llama)
        target_link_libraries(llama_engine PRIVATE llama)
    elseif(TARGET ggml)
        target_link_libraries(llama_engine PRIVATE ggml)
    endif()
endif()

find_library(log-lib log)
target_link_libraries(llama_engine PRIVATE ${log-lib})
