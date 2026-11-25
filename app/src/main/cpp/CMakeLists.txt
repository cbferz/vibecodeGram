cmake_minimum_required(VERSION 3.22.1)
project(vibecodegram_native LANGUAGES CXX)
add_library(vibecodegram_native SHARED native_engine.cpp)
target_compile_features(vibecodegram_native PRIVATE cxx_std_20)
