cmake_minimum_required(VERSION 3.18)
project(superres)

set(CMAKE_CXX_STANDARD 17)

# ncnn 由 CI 下载解压到 app/src/main/cpp/ncnn-android
set(NCNN_ROOT ${CMAKE_SOURCE_DIR}/ncnn-android)
set(NCNN_INCLUDE ${NCNN_ROOT}/include/ncnn)
set(NCNN_LIB ${NCNN_ROOT}/lib/${ANDROID_ABI}/libncnn.a)

if(NOT EXISTS ${NCNN_INCLUDE})
    message(FATAL_ERROR "ncnn not found at ${NCNN_ROOT}. CI should download ncnn-android-vulkan.zip first.")
endif()

add_library(superres SHARED sr_jni.cpp)

target_include_directories(superres PRIVATE ${NCNN_INCLUDE})

target_link_libraries(superres
    ${NCNN_LIB}
    android
    log
    z
)
