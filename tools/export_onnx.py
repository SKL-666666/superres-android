# -*- coding: utf-8 -*-
"""导出 4 个模型到 ONNX (动态尺寸)，供 pnnx 转 ncnn"""
import os, sys, types
import torch
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

def build_compact(key, nc):
    m = SRVGGNetCompact(num_feat=64, num_conv=nc, upscale=4, act_type='prelu')
    sd = torch.load(os.path.join(D, key+".pth"), map_location="cpu", weights_only=True)
    m.load_state_dict(sd["params"]); m.eval(); return m

def build_span():
    m = SPAN(num_in_ch=3, num_out_ch=3, feature_channels=48, upscale=4)
    sd = torch.load(os.path.join(D,"omd","4xNomosUni_span.pth"), map_location="cpu", weights_only=True)
    m.load_state_dict(sd.get("params_ema", sd.get("params", sd))); m.eval(); return m

outdir = os.path.join(D, "onnx")
os.makedirs(outdir, exist_ok=True)
jobs = [
    ("animevideov3", build_compact("realesr-animevideov3", 16)),
    ("x4v3",         build_compact("realesr-general-x4v3", 32)),
    ("wdn",          build_compact("realesr-general-wdn-x4v3", 32)),
    ("span",         build_span()),
]
dummy = torch.rand(1,3,128,128)
for name, m in jobs:
    p = os.path.join(outdir, name + ".onnx")
    torch.onnx.export(m, dummy, p, input_names=["in0"], output_names=["out0"],
                      dynamic_axes={"in0":{2:"h",3:"w"},"out0":{2:"h",3:"w"}},
                      opset_version=17, do_constant_folding=True)
    print(f"{name:14s} -> {p}  {os.path.getsize(p)/1024/1024:.2f} MB")
