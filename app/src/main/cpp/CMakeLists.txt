cmake_minimum_required(VERSION 3.22.1)

project("whatsup-engine")

add_library(node-runner SHARED
    node-runner.cpp
)

add_library(node SHARED IMPORTED)
set_target_properties(node PROPERTIES IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libnode.so
)

find_library(log-lib log)

target_link_libraries(node-runner
    node
    ${log-lib}
)
