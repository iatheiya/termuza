LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

ifeq ($(TARGET_ARCH_ABI), arm64-v8a)
BOOTSTRAP_SRC := termux-bootstrap-zip-arm64-v8a.S
else ifeq ($(TARGET_ARCH_ABI), armeabi-v7a)
BOOTSTRAP_SRC := termux-bootstrap-zip-armeabi-v7a.S
else ifeq ($(TARGET_ARCH_ABI), x86)
BOOTSTRAP_SRC := termux-bootstrap-zip-x86.S
else ifeq ($(TARGET_ARCH_ABI), x86_64)
BOOTSTRAP_SRC := termux-bootstrap-zip-x86_64.S
else
BOOTSTRAP_SRC := termux-bootstrap-zip.S
endif

LOCAL_SRC_FILES := $(BOOTSTRAP_SRC) termux-bootstrap.c
LOCAL_MODULE := libtermux-bootstrap
include $(BUILD_SHARED_LIBRARY)