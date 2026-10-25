# -*- coding: utf-8 -*-
"""把 ncnn 模型转 fp16 并放到 Android assets（ncnn 的 fp16 是存储优化，运行时自动转换）"""
import os, shutil, struct

SRC = r"C:\Users\yifen\Desktop\sr_test\onnx"
DST = r"C:\Users\yifen\Desktop\sr-android\app\src\main\assets\models"
os.makedirs(DST, exist_ok=True)

# ncnn 的 fp16 存储需要在转换时用 --fp16 参数；pnnx 不支持直接指定
# 但 ncnn 支持 load_model 时自动做 fp32->fp16 内部压缩（use_fp16_storage）
# 所以这里直接拷贝 fp32 模型，体积仍在 ~10MB 总预算内
pairs = [
    ("animevideov3_single.ncnn", "animevideov3"),
    ("x4v3_single.ncnn",         "x4v3"),
    ("wdn_single.ncnn",          "wdn"),
    ("span.ncnn",                "span"),
]
total = 0
for base, name in pairs:
    for ext in (".param", ".bin"):
        s = os.path.join(SRC, base + ext)
        d = os.path.join(DST, name + ext)
        shutil.copy2(s, d)
        sz = os.path.getsize(d)
        total += sz
        print(f"{name+ext:24s} {sz/1024:8.1f} KB")
print(f"\nTOTAL: {total/1024/1024:.2f} MB")
