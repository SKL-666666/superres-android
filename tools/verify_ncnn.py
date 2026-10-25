# -*- coding: utf-8 -*-
"""ncnn 转换数值校验：ncnn 输出 vs PyTorch 输出"""
import os, sys, types, time
import numpy as np, cv2
import torch
import ncnn
D = r"C:\Users\yifen\Desktop\sr_test"
sys.path.insert(0, D)
stub = types.ModuleType("basicsr"); u = types.ModuleType("basicsr.utils")
r = types.ModuleType("basicsr.utils.registry")
class Reg:
    def register(self, *a):
        if a: m=a[0]; m.registered=True; return m
        def deco(m): m.registered=True; return m
        return deco
r.ARCH_REGISTRY = Reg(); u.registry = r; stub.utils = u
sys.modules.update({"basicsr":stub,"basicsr.utils":u,"basicsr.utils.registry":r})
from srvgg_arch import SRVGGNetCompact
from span_arch import SPAN

def imread_u(p): return cv2.imdecode(np.fromfile(p,dtype=np.uint8), cv2.IMREAD_COLOR)
patch = imread_u(r"C:\Users\yifen\Pictures\Screenshots\屏幕截图 2026-08-21 154012.png")
patch = patch[300:428, 500:628]  # 128x128 BGR
rgb = cv2.cvtColor(patch, cv2.COLOR_BGR2RGB).astype(np.float32)/255.0

def to_t(bgr01):
    return torch.from_numpy(bgr01.transpose(2,0,1)[None]).float()

torch_in = torch.from_numpy(rgb.transpose(2,0,1)[None].copy()).float()

def build_pt(name):
    if name=="span":
        m = SPAN(3,3,feature_channels=48,upscale=4)
        sd = torch.load(os.path.join(D,"omd","4xNomosUni_span.pth"),map_location="cpu",weights_only=True)
        m.load_state_dict(sd.get("params_ema", sd.get("params", sd)))
    else:
        nc = 16 if name=="animevideov3" else 32
        key = {"animevideov3":"realesr-animevideov3","x4v3":"realesr-general-x4v3","wdn":"realesr-general-wdn-x4v3"}[name]
        m = SRVGGNetCompact(num_feat=64,num_conv=nc,upscale=4,act_type='prelu')
        m.load_state_dict(torch.load(os.path.join(D,key+".pth"),map_location="cpu",weights_only=True)["params"])
    m.eval(); return m

def run_ncnn(parambin, x_np):
    net = ncnn.Net()
    net.opt.use_vulkan_compute = False
    net.opt.num_threads = 4
    net.load_param(parambin+".param")
    net.load_model(parambin+".bin")
    ex = net.create_extractor()
    ex.input("in0", ncnn.Mat(x_np).clone())
    ret, out = ex.extract("out0")
    return np.array(out)

files = {
 "animevideov3": os.path.join(D,"onnx","animevideov3_single.ncnn"),
 "x4v3":         os.path.join(D,"onnx","x4v3_single.ncnn"),
 "wdn":          os.path.join(D,"onnx","wdn_single.ncnn"),
 "span":         os.path.join(D,"onnx","span.ncnn"),
}
print(f"{'model':14s} {'max_abs_err':>12s} {'PSNR(ncnn vs pt)':>18s} {'ncnn_s':>8s}")
for name, base in files.items():
    m = build_pt(name)
    with torch.no_grad():
        t0=time.time(); ref = m(torch_in).numpy()[0]; t_pt=time.time()-t0
    x_in = np.ascontiguousarray(rgb.transpose(2,0,1))
    t0=time.time(); got = run_ncnn(base, x_in); t_nn=time.time()-t0
    refc = np.clip(ref,0,1); gotc = np.clip(got,0,1)
    err = np.abs(refc-gotc).max()
    mse = np.mean((refc-gotc)**2)
    psnr = 99 if mse<1e-12 else 10*np.log10(1.0/mse)
    print(f"{name:14s} {err:12.5f} {psnr:18.2f} {t_nn:8.2f}")
