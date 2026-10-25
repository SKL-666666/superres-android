cmake_minimum_required(VERSION 3.18)
project(superres)

set(CMAKE_CXX_STANDARD 17)

# ncnn 目录结构：ncnn-android/<abi>/{include,lib}
set(NCNN_ROOT  ${CMAKE_SOURCE_DIR}/ncnn-android)
set(NCNN_ABI   ${NCNN_ROOT}/${ANDROID_ABI})
set(NCNN_INC   ${NCNN_ABI}/include/ncnn)
set(NCNN_LIB   ${NCNN_ABI}/lib/libncnn.a)

if(NOT EXISTS ${NCNN_LIB})
    message(FATAL_ERROR "libncnn.a not found: ${NCNN_LIB}. CI must download ncnn first.")
endif()

add_library(superres SHARED sr_jni.cpp)

target_include_directories(superres PRIVATE ${NCNN_INC})

find_library(LOG_LIB     log)
find_library(ANDROID_LIB android)

# 预编译 ncnn 依赖 OpenMP（其 .a 内含 __kmpc_* 引用）。
# NDK 自带 libomp，用 -fopenmp + -static-openmp 静态链入，无需额外拷贝 .so。
target_compile_options(superres PRIVATE -fopenmp)
target_link_options(superres PRIVATE -fopenmp -static-openmp)

target_link_libraries(superres
    ${NCNN_LIB}
    ${ANDROID_LIB}
    ${LOG_LIB}
    z
)
