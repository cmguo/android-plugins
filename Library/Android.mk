# plugin-android

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE	:= DroidPlugins

LOCAL_SRC_FILES	:= $(call all-java-files-under,src)

include $(BUILD_STATIC_JAVA_LIBRARY)
