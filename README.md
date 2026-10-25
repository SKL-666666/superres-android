# SuperRes · 画质增强

Android 本地图片超分 App。**纯 CPU 推理、离线运行、不上传任何数据**。
内置 4 个经实测横评入选的模型，用户可按题材自由切换。

支持骁龙与麒麟芯片（arm64-v8a），一套 APK 通用，无需区分平台。

---

## 功能

- 一次最多选 **9 张**图片批量增强
- 四模型切换：SPAN / AnimeVideo v3 / Real-ESRGAN v3 / WDN v3
- 倍率选择：×2（快）/ ×4（大）
- 处理进度实时显示，可中途取消
- 结果一键保存至相册 `Pictures/SuperRes`
- 支持从其他 App（相册、微信等）**分享直接跳转**，也可在 App 内选图
- 输入长边自动限制在 2000px 以内，超大图自动分块处理，避免手机内存爆掉

## 模型清单

| 模型 | 参数量 | ncnn 权重 | 特点 | 适用场景 |
|---|---|---|---|---|
| **SPAN**（NomosUni）| 2.24M | 835 KB | 结构锐利、无 GAN 幻觉 | 文字、线条、建筑、截图 |
| **AnimeVideo v3** | 0.62M | 1.2 MB | 自然柔和、速度最快 | 日常随手拍、低内存设备 |
| **Real-ESRGAN v3** | 1.21M | 2.4 MB | 通透锐利、细节最丰富 | 想要"更锐"的用户 |
| **WDN v3** | 1.21M | 2.4 MB | 低噪稳健 | 夜景、霓虹、高噪场景 |

四个模型**全部内置**，离线可用，无网络请求。

## 构建

### 方式一：直接下载 APK（推荐）

打开仓库 Releases 页，下载最新 `app-debug.apk` 安装即可。

<https://github.com/SKL-666666/superres-android/releases>

### 方式二：GitHub Actions 自动构建

推送到 `main` 分支即自动触发构建，并创建 Release。

### 方式三：本地构建

```bash
# 1. 下载 ncnn Android 预编译包
TAG=$(curl -fsSL https://api.github.com/repos/Tencent/ncnn/releases/latest | grep -m1 '"tag_name"' | cut -d'"' -f4)
curl -fL -o ncnn.zip "https://github.com/Tencent/ncnn/releases/download/${TAG}/ncnn-${TAG}-android.zip"
unzip ncnn.zip
mv "ncnn-${TAG}-android" app/src/main/cpp/ncnn-android

# 2. 构建
gradle assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

> 注意：ncnn 预编译包**未附带 OpenMP 运行时**，构建时通过 `-fopenmp -static-openmp` 链接 NDK 自带 libomp，见 `app/src/main/cpp/CMakeLists.txt`。

## 技术实现

### 推理框架

[ncnn](https://github.com/Tencent/ncnn) 静态库，`use_vulkan_compute = false` 纯 CPU。
开启 fp16 packed / storage / arithmetic / packing_layout 四项优化，以及 winograd + sgemm 卷积。

### 性能优化

| 优化项 | 说明 |
|---|---|
| 大核亲和 | 8 核机型绑定后 4 核（大核区），失败回退全核 |
| 线程数留余 | `ncpu>=8` 用 6 线程，`ncpu==4` 用 4 线程，留 1~2 核给 UI 和解码 |
| 分块推理 | 256px tile + 8px padding，逐块回写，峰值内存可控 |
| 2x2 平均降采样 | ×2 输出时在 C++ 内直接 4x→2x 平均，避免大临时数组 |
| 输入限幅 | 长边 ≤2000px 自动降采样，匹配"512–2000 最佳效果"经验值 |

### 跨芯片说明

**代码中没有针对骁龙、麒麟的任何分支**：绑核采用"核编号后半段"策略并带失败回退，两种大小核拓扑都能正确运行。纯 CPU + 通用 arm64 指令，不依赖 Hexagon（骁龙）或达芬奇（麒麟）的私有 API。

因此**一套 APK 在骁龙和麒麟上行为完全一致**，性能差异仅来自核心数与频率，而非代码分支。

### 模型转换

转换脚本位于 `tools/`，可复现全部模型：

```bash
pip install torch pnnx ncnn opencv-python-headless
cd tools
python export_onnx.py          # PyTorch -> ONNX（SRVGGNetCompact 三个模型）
python export_span.py          # SPAN 专用：先触发 Conv3XC 重参数化再导出
python export_single.py        # 转单文件 ONNX（内嵌权重，pnnx 可读）
pnnx onnx/<name>_single.onnx outputdir=ncnn   # ONNX -> ncnn
python verify_ncnn.py          # 数值校验：ncnn 输出 vs PyTorch 输出
python pack_models.py          # 打包到 app/src/main/assets/models/
```

`verify_ncnn.py` 会逐模型对比 ncnn 与 PyTorch 的输出，输出 PSNR（实测 71–85 dB，误差远低于视觉可见阈值）。

## 实测数据

测试环境：i5-1130G7 @1.1GHz（4核8线程），纯 CPU fp32，4 线程。
换算到 Mate60（麒麟 9000S）约除以 2~2.5；ncnn 已启用 fp16 优化。

### 全图 4 模型对比（输入 1.71~1.90 MP）

| 模型 | PSNR | SSIM | 锐度 | 耗时 |
|---|---|---|---|---|
| **SPAN** | **30.8~33.6** | **0.92~0.96** | 90~324 | **22~27s** |
| av3 | 28.5~32.9 | 0.93~0.95 | 71~392 | 21~24s |
| wdn | 26.9~32.2 | 0.89~0.93 | 247~850 | 39~47s |
| x4v3 | 24.7~29.9 | 0.89~0.93 | 284~1022 | 42~47s |

> PSNR/SSIM 以原图 bicubic×2 为真值。x4v3 锐度最高但指标垫底——它把噪点一并锐化了。

### 三张街景截图的一致规律

- SPAN 在 **PSNR / SSIM 两项指标上三连冠**，且无 GAN 幻觉颗粒
- x4v3 / wdn 天空区域可见细微颗粒（GAN 系通病）
- x4v3 速度最慢（约为 SPAN 的 1.8 倍）

**主观排序由用户实测决定**：指标测不出"更通透"这种观感，最终排名以用户眼睛为准。

## 已知限制

- 输出为 JPEG（q=95），保留 EXIF 未做（可后续补）
- 未做 Int8 量化（fp16 已够用，量化可再省 2x 时间但可能掉画质）
- 未适配 armeabi-v7a / x86（仅 arm64-v8a）
- 鸿蒙卓易通兼容层下，相册/文件权限代理可能不完整，若保存失败需改用 Storage Access Framework

## License

App 代码：MIT
模型权重：见各模型原仓库
- SPAN: NomosUni (HuggingFace @Nudimmud)
- AnimeVideo v3 / Real-ESRGAN v3 / WDN v3: xinntao/Real-ESRGAN
