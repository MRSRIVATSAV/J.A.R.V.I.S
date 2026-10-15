# Install script for directory: /workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml

# Set the install prefix
if(NOT DEFINED CMAKE_INSTALL_PREFIX)
  set(CMAKE_INSTALL_PREFIX "/usr/local")
endif()
string(REGEX REPLACE "/$" "" CMAKE_INSTALL_PREFIX "${CMAKE_INSTALL_PREFIX}")

# Set the install configuration name.
if(NOT DEFINED CMAKE_INSTALL_CONFIG_NAME)
  if(BUILD_TYPE)
    string(REGEX REPLACE "^[^A-Za-z0-9_]+" ""
           CMAKE_INSTALL_CONFIG_NAME "${BUILD_TYPE}")
  else()
    set(CMAKE_INSTALL_CONFIG_NAME "Debug")
  endif()
  message(STATUS "Install configuration: \"${CMAKE_INSTALL_CONFIG_NAME}\"")
endif()

# Set the component getting installed.
if(NOT CMAKE_INSTALL_COMPONENT)
  if(COMPONENT)
    message(STATUS "Install component: \"${COMPONENT}\"")
    set(CMAKE_INSTALL_COMPONENT "${COMPONENT}")
  else()
    set(CMAKE_INSTALL_COMPONENT)
  endif()
endif()

# Install shared libraries without execute permission?
if(NOT DEFINED CMAKE_INSTALL_SO_NO_EXE)
  set(CMAKE_INSTALL_SO_NO_EXE "1")
endif()

# Is this installation the result of a crosscompile?
if(NOT DEFINED CMAKE_CROSSCOMPILING)
  set(CMAKE_CROSSCOMPILING "TRUE")
endif()

# Set path to fallback-tool for dependency-resolution.
if(NOT DEFINED CMAKE_OBJDUMP)
  set(CMAKE_OBJDUMP "/tmp/android-sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-objdump")
endif()

if(NOT CMAKE_INSTALL_LOCAL_ONLY)
  # Include the install script for the subdirectory.
  include("/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-build/ggml/src/cmake_install.cmake")
endif()

if(CMAKE_INSTALL_COMPONENT STREQUAL "Unspecified" OR NOT CMAKE_INSTALL_COMPONENT)
  if(EXISTS "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/libggml.so" AND
     NOT IS_SYMLINK "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/libggml.so")
    file(RPATH_CHECK
         FILE "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/libggml.so"
         RPATH "")
  endif()
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib" TYPE SHARED_LIBRARY FILES "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/bin/libggml.so")
  if(EXISTS "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/libggml.so" AND
     NOT IS_SYMLINK "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/libggml.so")
    if(CMAKE_INSTALL_DO_STRIP)
      execute_process(COMMAND "/tmp/android-sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/libggml.so")
    endif()
  endif()
endif()

if(CMAKE_INSTALL_COMPONENT STREQUAL "Unspecified" OR NOT CMAKE_INSTALL_COMPONENT)
endif()

if(CMAKE_INSTALL_COMPONENT STREQUAL "Unspecified" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/include" TYPE FILE FILES
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml/include/ggml.h"
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml/include/ggml-cpu.h"
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml/include/ggml-alloc.h"
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml/include/ggml-backend.h"
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml/include/ggml-blas.h"
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml/include/ggml-cann.h"
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml/include/ggml-cpp.h"
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml/include/ggml-cuda.h"
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml/include/ggml-opt.h"
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml/include/ggml-metal.h"
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml/include/ggml-rpc.h"
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml/include/ggml-virtgpu.h"
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml/include/ggml-sycl.h"
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml/include/ggml-vulkan.h"
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml/include/ggml-webgpu.h"
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml/include/ggml-zendnn.h"
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml/include/ggml-openvino.h"
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-src/ggml/include/gguf.h"
    )
endif()

if(CMAKE_INSTALL_COMPONENT STREQUAL "Unspecified" OR NOT CMAKE_INSTALL_COMPONENT)
  if(EXISTS "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/libggml-base.so" AND
     NOT IS_SYMLINK "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/libggml-base.so")
    file(RPATH_CHECK
         FILE "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/libggml-base.so"
         RPATH "")
  endif()
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib" TYPE SHARED_LIBRARY FILES "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/bin/libggml-base.so")
  if(EXISTS "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/libggml-base.so" AND
     NOT IS_SYMLINK "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/libggml-base.so")
    if(CMAKE_INSTALL_DO_STRIP)
      execute_process(COMMAND "/tmp/android-sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/libggml-base.so")
    endif()
  endif()
endif()

if(CMAKE_INSTALL_COMPONENT STREQUAL "Unspecified" OR NOT CMAKE_INSTALL_COMPONENT)
endif()

if(CMAKE_INSTALL_COMPONENT STREQUAL "Unspecified" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/cmake/ggml" TYPE FILE FILES
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-build/ggml/ggml-config.cmake"
    "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-build/ggml/ggml-config-version.cmake"
    )
endif()

string(REPLACE ";" "\n" CMAKE_INSTALL_MANIFEST_CONTENT
       "${CMAKE_INSTALL_MANIFEST_FILES}")
if(CMAKE_INSTALL_LOCAL_ONLY)
  file(WRITE "/workspaces/J.A.R.V.I.S/app/.cxx/Debug/v2x6a2h2/arm64-v8a/_deps/llama_cpp-build/ggml/install_local_manifest.txt"
     "${CMAKE_INSTALL_MANIFEST_CONTENT}")
endif()
